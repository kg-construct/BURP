package burp.dev.rml.service.validator;

import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.completion.ReferenceSchemaResolver;
import burp.dev.rml.service.schema.SqlSchemaProvider;
import burp.ls.JSONPathError;
import burp.ls.JSONSourceRFC;
import burp.ls.RDBSource;
import burp.model.*;
import burp.parse.turtleprov.Point;
import burp.reporting.Origin;
import burp.reporting.PointRange;
import burp.reporting.RmlError;
import burp.reporting.StatementPart;
import burp.reporting.TextFilePointer;
import burp.vocabularies.RER;
import burp.vocabularies.Rml;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates reference expressions (JSONPath syntax and schema/data conformance)
 * and RDB logical sources (connection status and SQL query syntax).
 * Template string syntax validation is handled by TemplateParser during loading.
 */
@NullMarked
public class ReferenceValidator implements MappingValidator {

    public static final String ID = "data-references";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public ValidationExecutionPolicy getExecutionPolicy() {
        return ValidationExecutionPolicy.DEBOUNCED_ASYNC;
    }

    @Override
    public List<RmlError> validate(ValidationContext context) {
        List<RmlError> errors = new ArrayList<>();
        List<PlanNode> allNodes = new ArrayList<>();
        context.workspace().collectAllNodes(context.loaded().document(), allNodes);
        validate(allNodes, context.loaded().document().triplesMaps, context.workspace(), errors);
        return errors;
    }

    public void validate(
            List<PlanNode> allNodes,
            List<TriplesMap> triplesMaps,
            MappingWorkspace workspace,
            List<RmlError> errors
    ) {
        SqlSchemaProvider sqlProvider = new SqlSchemaProvider(workspace.getCache());

        // 1. Validate RDB Logical Sources FIRST (connection status and SQL query syntax)
        Set<RDBSource> validatedSources = new HashSet<>();
        for (TriplesMap tm : triplesMaps) {
            if (tm.logicalSource instanceof RDBSource rdb && validatedSources.add(rdb)) {
                validateRdbSource(rdb, tm, sqlProvider, errors);
            }
        }

        // 2. Validate all references (both standalone and inside templates)
        for (PlanNode node : allNodes) {
            if (node instanceof Reference r && !(node.getParent() instanceof RawReference)) {
                validateReference(r, sqlProvider, workspace.getIterationSampleLimit(), errors);
            }
        }
    }

    private void validateReference(
            Reference refNode,
            SqlSchemaProvider sqlProvider,
            @Nullable Integer iterationSampleLimit,
            List<RmlError> errors
    ) {
        String ref = refNode.getReference();
        if (ref == null || ref.isBlank()) {
            return;
        }

        boolean isTemplateSegment = refNode.getParent() instanceof Template.ReferenceSegment;
        Origin origin = switch (refNode.getParent()) {
            case Template.ReferenceSegment seg -> MappingValidator.firstNodeRangeOrigin(seg);
            case null, default -> {
                Origin refOrigin = refNode.emitOrigin("reference", StatementPart.Object);
                if (!refOrigin.ranges().isEmpty()) {
                    yield refOrigin;
                }
                yield MappingValidator.firstNodeRangeOrigin(refNode);
            }
        };

        if (origin == null || origin.ranges().isEmpty()) {
            return;
        }

        AbstractLogicalSource ls = ReferenceSchemaResolver.resolveContextualLogicalSource(refNode);
        if (ls instanceof RDBSource rdb) {
            var err = sqlProvider.getError(rdb);
            if (err != null) {
                // Suppress downstream column warnings if DB connection or query failed
                return;
            }
        }

        if (ls == null) {
            return; // Without logical source we cannot validate the reference
        }
        ReferenceSchemaResolver.SchemaInfo schemaInfo = ReferenceSchemaResolver.extractSchemaInfo(refNode, ls, iterationSampleLimit);

        // Syntax checking for JSONPath
        if (schemaInfo.referenceFormulation().equals(Rml.JSONPath)) {
            List<JSONPathError> syntaxErrors = JSONSourceRFC.validateJsonPath(ref);
            PointRange baseRange = origin.ranges().getFirst();
            for (JSONPathError se : syntaxErrors) {
                String msg = isTemplateSegment
                        ? "Invalid JSONPath in template: " + se.message()
                        : "Invalid JSONPath reference: " + se.message();
                Origin errOrigin = origin;
                if (se.start() != null && isTemplateSegment && refNode.getParent() instanceof Template.ReferenceSegment seg) {
                    int mappedCol = seg.offsetMapper().toRawOffset(se.start().column());
                    Point errStart = new Point(baseRange.start().line(), baseRange.start().column() + mappedCol);
                    PointRange errRange = new PointRange(errStart, baseRange.end());
                    errOrigin = new Origin(List.of(new TextFilePointer(Path.of(""), errRange)));
                }
                errors.add(new RmlError(
                        msg,
                        errOrigin,
                        RER.ReferenceFormulationSyntaxError
                ));
            }
        }

        // Schema & Sampled data conformance
        if (schemaInfo.hasKnownSchemaOrData()) {
            boolean found = matchesReference(ref, schemaInfo.candidateValues().keySet(), ls, sqlProvider);
            if (!found) {
                String msg = isTemplateSegment
                        ? "Template reference '{" + ref + "}' was not found in source schema or dataset."
                        : "Reference '" + ref + "' was not found in source schema or dataset.";
                errors.add(new RmlError(
                        msg,
                        origin,
                        RER.ReferenceFormulationExecutionError
                ));
            }
        }
    }

    private boolean matchesReference(String ref, Set<String> candidates, @Nullable AbstractLogicalSource ls, SqlSchemaProvider sqlProvider) {
        if (ls instanceof RDBSource rdb) {
            SqlSchemaProvider.SqlDialectRules rules = sqlProvider.getDialectRules(rdb);
            return rules.matches(ref, candidates);
        }

        if (candidates.contains(ref)) return true;
        if (ref.startsWith("$.") && candidates.contains(ref.substring(2))) return true;
        if (!ref.startsWith("$.") && candidates.contains("$." + ref)) return true;

        for (String c : candidates) {
            if (c.equals(ref) || c.startsWith(ref + ".") || c.startsWith(ref + "[") || ref.startsWith(c + ".")) {
                return true;
            }
        }
        return false;
    }

    private void validateRdbSource(RDBSource rdb, TriplesMap tm, SqlSchemaProvider sqlProvider, List<RmlError> errors) {
        sqlProvider.extractSchema(rdb);
        SqlSchemaProvider.SqlSourceError error = sqlProvider.getError(rdb);
        if (error == null) {
            return;
        }

        Origin sourceOrigin = null;
        if (error.kind() == SqlSchemaProvider.ErrorKind.QUERY_ERROR) {
            Origin queryOrigin = rdb.emitOrigin("query", StatementPart.Object);
            if (!queryOrigin.ranges().isEmpty()) {
                sourceOrigin = queryOrigin;
            }
        }
        if (sourceOrigin == null) {
            sourceOrigin = MappingValidator.firstNodeRangeOrigin(rdb);
        }
        if (sourceOrigin == null) {
            sourceOrigin = MappingValidator.firstNodeRangeOrigin(tm);
        }
        if (sourceOrigin == null) {
            return;
        }

        if (error.kind() == SqlSchemaProvider.ErrorKind.CONNECTION_ERROR) {
            errors.add(new RmlError(
                    error.message(),
                    sourceOrigin,
                    RER.SourceAccessError
            ));
        } else if (error.kind() == SqlSchemaProvider.ErrorKind.QUERY_ERROR) {
            errors.add(new RmlError(
                    error.message(),
                    sourceOrigin,
                    RER.IteratorSyntaxError
            ));
        }
    }
}
