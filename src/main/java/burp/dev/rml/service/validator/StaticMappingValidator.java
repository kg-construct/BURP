package burp.dev.rml.service.validator;

import burp.dev.rml.service.MappingWorkspace;
import burp.model.PlanNode;
import burp.parse.turtleprov.Point;
import burp.parse.turtleprov.ProvTurtleVisitor;
import burp.reporting.*;
import burp.vocabularies.RER;
import org.apache.jena.rdf.model.Statement;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * High-level orchestrator for static analysis on RML mappings.
 * Coordinates ReferenceValidator, FunctionExecutionValidator, UnusedElementValidator,
 * and SHACL shape / Turtle parsing reports.
 */
@NullMarked
public class StaticMappingValidator {

    private final ReferenceValidator referenceValidator;
    private final FunctionExecutionValidator functionExecutionValidator;
    private final UnusedElementValidator unusedElementValidator;

    public StaticMappingValidator() {
        this(new ReferenceValidator(), new FunctionExecutionValidator(), new UnusedElementValidator());
    }

    public StaticMappingValidator(
            ReferenceValidator referenceValidator,
            FunctionExecutionValidator functionExecutionValidator,
            UnusedElementValidator unusedElementValidator
    ) {
        this.referenceValidator = referenceValidator;
        this.functionExecutionValidator = functionExecutionValidator;
        this.unusedElementValidator = unusedElementValidator;
    }

    public CompletableFuture<List<RmlDiagnostic>> validate(
            @Nullable ValidateParams params,
            MappingWorkspace workspace
    ) {
        if (params == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        try {
            MappingWorkspace.LoadedMapping loaded = workspace.loadMappingDetailed(params.mappingFileUri(), burp.parse.ValidationMode.LENIENT);
            List<RmlDiagnostic> diagnostics = new ArrayList<>();

            // 1. Incorporate report errors (SHACL shape violations & turtle parsing errors)
            validateReportErrors(loaded.report(), diagnostics);

            // Collect all AST nodes across TriplesMaps
            List<PlanNode> allNodes = new ArrayList<>();
            workspace.collectAllNodes(loaded.document(), allNodes);

            // 2. Validate references & templates
            referenceValidator.validate(allNodes, loaded.document().triplesMaps, workspace, diagnostics);

            // 3. Validate function executions
            Map<String, String> prefixMap = new HashMap<>(loaded.document().prefixMap);
            if (loaded.model() != null) {
                prefixMap.putAll(loaded.model().getNsPrefixMap());
            }
            functionExecutionValidator.validate(allNodes, diagnostics, prefixMap);

            // 4. Validate unused RML elements and orphaned structures
            if (loaded.model() != null) {
                Set<Statement> accessed = loaded.loader().getReflectiveLoader().getAccessedStatements();
                unusedElementValidator.validate(loaded.model(), accessed, diagnostics);
            }

            // Deduplicate and sort diagnostics
            List<RmlDiagnostic> deduplicated = deduplicateAndSort(diagnostics);
            return CompletableFuture.completedFuture(deduplicated);
        } catch (BurpException be) {
            Range range = resolveOriginRange(be.getError() != null ? be.getError().origin() : null);
            return CompletableFuture.completedFuture(List.of(new RmlDiagnostic(
                    range,
                    DiagnosticSeverity.Error,
                    "RML_LOAD_ERROR",
                    "Failed to validate mapping: " + (be.getMessage() != null ? be.getMessage() : be.toString()),
                    "RER Validator"
            )));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(List.of(new RmlDiagnostic(
                    null,
                    DiagnosticSeverity.Error,
                    "RML_LOAD_ERROR",
                    "Failed to validate mapping: " + (e.getMessage() != null ? e.getMessage() : e.toString()),
                    "BURP Validator"
            )));
        }
    }

    private void validateReportErrors(@Nullable RmlExecutionReport report, List<RmlDiagnostic> diagnostics) {
        if (report == null || report.getErrors() == null) {
            return;
        }

        for (RmlError error : report.getErrors()) {
            Range range = resolveOriginRange(error.origin());

            DiagnosticSeverity severity = DiagnosticSeverity.Error;
            String code = error.errorType().getURI() != null
                    ? error.errorType().getURI()
                    : RER.MappingError.getURI();

            diagnostics.add(new RmlDiagnostic(
                    range,
                    severity,
                    code,
                    error.message(),
                    "BURP Parser"
            ));
        }
    }

    private @Nullable Range resolveOriginRange(@Nullable Origin origin) {
        if (origin == null || origin.sourceStatements() == null) {
            return null;
        }
        List<PointRange> ranges = ProvTurtleVisitor.retrieveTurtleLocation(origin.sourceStatements());
        if (ranges.isEmpty()) {
            return null;
        }
        Point start = ranges.getFirst().start();
        Point end = ranges.getLast().end() != null ? ranges.getLast().end() : ranges.getLast().start();
        return new Range(start.toLsp(), end.toLsp());
    }

    private List<RmlDiagnostic> deduplicateAndSort(List<RmlDiagnostic> list) {
        Map<String, RmlDiagnostic> uniqueWithRange = new LinkedHashMap<>();
        Map<String, RmlDiagnostic> uniqueWithoutRange = new LinkedHashMap<>();

        for (RmlDiagnostic d : list) {
            Range range = d.range();
            if (range != null) {
                String key = range.getStart().getLine() + ":" + range.getStart().getCharacter()
                        + ":" + d.severity() + ":" + d.message();
                uniqueWithRange.putIfAbsent(key, d);
            } else {
                String key = "null:" + d.severity() + ":" + d.message();
                uniqueWithoutRange.putIfAbsent(key, d);
            }
        }

        List<RmlDiagnostic> result = new ArrayList<>(uniqueWithRange.values());
        result.sort(Comparator.comparingInt((RmlDiagnostic d) -> Objects.requireNonNull(d.range()).getStart().getLine())
                .thenComparingInt(d -> Objects.requireNonNull(d.range()).getStart().getCharacter()));
        result.addAll(uniqueWithoutRange.values());
        return result;
    }
}
