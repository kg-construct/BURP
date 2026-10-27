package burp.dev.rml.service.validator;

import burp.dev.rml.service.plan.PlanNodeDescriptor;
import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.completion.ReferenceSchemaResolver;
import burp.dev.rml.service.schema.SqlSchemaProvider;
import burp.ls.JSONPathError;
import burp.ls.JSONSourceRFC;
import burp.ls.RDBSource;
import burp.model.*;
import burp.parse.turtleprov.ProvTurtleVisitor;
import burp.reporting.PointRange;
import burp.reporting.StatementPart;
import burp.reporting.Origin;
import burp.vocabularies.RER;
import burp.vocabularies.Rml;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates reference expressions (JSONPath syntax and schema/data conformance)
 * and RDB logical sources (connection status and SQL query syntax).
 * Template string syntax validation is handled by TemplateParser during loading.
 */
@NullMarked
public class ReferenceValidator {

    public void validate(
            List<PlanNode> allNodes,
            List<TriplesMap> triplesMaps,
            MappingWorkspace workspace,
            List<RmlDiagnostic> diagnostics
    ) {
        SqlSchemaProvider sqlProvider = new SqlSchemaProvider(workspace.getCache());

        // 1. Validate RDB Logical Sources FIRST (connection status and SQL query syntax)
        Set<RDBSource> validatedSources = new HashSet<>();
        for (TriplesMap tm : triplesMaps) {
            if (tm.logicalSource instanceof RDBSource rdb && validatedSources.add(rdb)) {
                validateRdbSource(rdb, tm, sqlProvider, diagnostics);
            }
        }

        // 2. Validate all references (both standalone and inside templates)
        for (PlanNode node : allNodes) {
            if (node instanceof Reference r && !(node.getParent() instanceof RawReference)) {
                validateReference(r, sqlProvider, workspace.getIterationSampleLimit(), diagnostics);
            }
        }
    }

    private void validateReference(
            Reference refNode,
            SqlSchemaProvider sqlProvider,
            @Nullable Integer iterationSampleLimit,
            List<RmlDiagnostic> diagnostics
    ) {
        String ref = refNode.getReference();
        if (ref == null || ref.isBlank()) {
            return;
        }

        boolean isTemplateSegment = refNode.getParent() instanceof Template.ReferenceSegment;
        Range range = switch (refNode.getParent()) {
            case Template.ReferenceSegment seg -> PlanNodeDescriptor.getFirstRangeForNode(seg);
            case null, default -> {
                Origin refOrigin = refNode.emitOrigin("reference", StatementPart.Object);
                if (refOrigin.sourceStatements() != null) {
                    List<PointRange> ranges = ProvTurtleVisitor.retrieveTurtleLocation(refOrigin.sourceStatements());
                    if (!ranges.isEmpty()) {
                        yield ranges.getFirst().toLsp();
                    }
                }
                yield PlanNodeDescriptor.getFirstRangeForNode(refNode);
            }
        };

        if (range == null) {
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
            for (JSONPathError se : syntaxErrors) {
                String msg = isTemplateSegment
                        ? "Invalid JSONPath in template: " + se.message()
                        : "Invalid JSONPath reference: " + se.message();
                Range errRange = range;
                if (se.start() != null && isTemplateSegment && refNode.getParent() instanceof Template.ReferenceSegment seg) {
                    int mappedCol = seg.offsetMapper().toRawOffset(se.start().column());
                    Position errPos = new Position(range.getStart().getLine(), range.getStart().getCharacter() + mappedCol);
                    errRange = new Range(errPos, range.getEnd());
                }
                diagnostics.add(new RmlDiagnostic(
                        errRange,
                        DiagnosticSeverity.Error,
                        RER.ReferenceFormulationSyntaxError.getURI(),
                        msg,
                        "BURP Validator"
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
                diagnostics.add(new RmlDiagnostic(
                        range,
                        DiagnosticSeverity.Warning,
                        RER.ReferenceFormulationExecutionError.getURI(),
                        msg,
                        "BURP Validator"
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

    private void validateRdbSource(RDBSource rdb, TriplesMap tm, SqlSchemaProvider sqlProvider, List<RmlDiagnostic> diagnostics) {
        sqlProvider.extractSchema(rdb);
        SqlSchemaProvider.SqlSourceError error = sqlProvider.getError(rdb);
        if (error == null) {
            return;
        }

        Range sourceRange = null;
        if (error.kind() == SqlSchemaProvider.ErrorKind.QUERY_ERROR) {
            Origin queryOrigin = rdb.emitOrigin("query", StatementPart.Object);
            if (queryOrigin.sourceStatements() != null) {
                List<PointRange> ranges = ProvTurtleVisitor.retrieveTurtleLocation(queryOrigin.sourceStatements());
                if (!ranges.isEmpty()) {
                    sourceRange = ranges.getFirst().toLsp();
                }
            }
        }
        if (sourceRange == null) {
            sourceRange = PlanNodeDescriptor.getFirstRangeForNode(rdb);
        }
        if (sourceRange == null) {
            sourceRange = PlanNodeDescriptor.getFirstRangeForNode(tm);
        }
        if (sourceRange == null) {
            return;
        }

        if (error.kind() == SqlSchemaProvider.ErrorKind.CONNECTION_ERROR) {
            diagnostics.add(new RmlDiagnostic(
                    sourceRange,
                    DiagnosticSeverity.Information,
                    RER.SourceAccessError.getURI(),
                    error.message(),
                    "BURP Validator"
            ));
        } else if (error.kind() == SqlSchemaProvider.ErrorKind.QUERY_ERROR) {
            diagnostics.add(new RmlDiagnostic(
                    sourceRange,
                    DiagnosticSeverity.Error,
                    RER.ReferenceFormulationSyntaxError.getURI(),
                    error.message(),
                    "BURP Validator"
            ));
        }
    }
}
