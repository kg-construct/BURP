package burp.dev.rml.service.source;

import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.hint.ExpressionFormatter;
import burp.dev.rml.service.plan.PlanNodeDescriptor;
import burp.model.AbstractLogicalSource;
import burp.model.AbstractPlanNode;
import burp.model.MappingDocument;
import burp.model.PlanNode;
import burp.model.fnml.FunctionExecution;
import burp.model.lv.LogicalView;
import burp.parse.turtleprov.ProvTurtleVisitor;
import burp.reporting.Origin;
import burp.reporting.PointRange;
import burp.reporting.StatementPart;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/// Service providing standard LSP CodeLenses (textDocument/codeLens)
/// for actionable RML constructs (such as previewing Logical Views)
/// and static function call syntax hints above function executions.
@NullMarked
public class LspCodeLensProvider {

    public CompletableFuture<List<? extends CodeLens>> provideCodeLenses(
            @Nullable CodeLensParams params,
            MappingWorkspace workspace
    ) {
        if (params == null || params.getTextDocument() == null || params.getTextDocument().getUri() == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        try {
            String mappingFileUri = params.getTextDocument().getUri();
            var document = workspace.loadMappingWithStableIds(mappingFileUri);
            List<CodeLens> codeLenses = new ArrayList<>();
            Set<String> seenPositions = new HashSet<>();

            provideDataPreviewLenses(document, mappingFileUri, codeLenses, seenPositions);
            provideFunctionPreviewLenses(document, workspace.getPrefixMap(mappingFileUri), codeLenses, seenPositions);

            return CompletableFuture.completedFuture(codeLenses);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
    }

    private void provideDataPreviewLenses(
            MappingDocument document,
            String mappingFileUri,
            List<CodeLens> codeLenses,
            Set<String> seenPositions
    ) {
        document.descendantsAndReferences(AbstractLogicalSource.class).forEach(ls -> {
            Range nodeRange = PlanNodeDescriptor.getFirstRangeForNode(ls);
            if (nodeRange != null && nodeRange.getStart() != null) {
                Range lensRange = new Range(nodeRange.getStart(), nodeRange.getStart());
                String iriStr = PlanNode.resourceToIriOrBNode(ls.getIri());
                Command command = getCommand(ls, iriStr, mappingFileUri);
                String key = nodeRange.getStart().getLine() + ":" + nodeRange.getStart().getCharacter() + ":" + command.getTitle();
                if (seenPositions.add(key)) {
                    codeLenses.add(new CodeLens(lensRange, command, null));
                }
            }
        });
    }

    private void provideFunctionPreviewLenses(
            MappingDocument document,
            @Nullable Map<String, String> prefixMap,
            List<CodeLens> codeLenses,
            Set<String> seenPositions
    ) {
        document.descendants(FunctionExecution.class).forEach(fe -> {
            String title = ExpressionFormatter.formatFunctionExecution(fe, prefixMap);
            if (title.isEmpty()) {
                return;
            }

            List<Range> targetRanges = getFunctionRanges(fe);
            for (Range r : targetRanges) {
                if (r.getStart() != null) {
                    Range lensRange = new Range(r.getStart(), r.getStart());
                    Command command = new Command(title, "");
                    String key = r.getStart().getLine() + ":" + r.getStart().getCharacter() + ":" + title;
                    if (seenPositions.add(key)) {
                        codeLenses.add(new CodeLens(lensRange, command, null));
                    }
                }
            }
        });
    }

    private List<Range> getFunctionRanges(FunctionExecution fe) {
        List<Range> ranges = new ArrayList<>();

        // 1. Definition site (subject of functionMap declaration)
        Origin defOrigin = fe.emitOrigin("functionMap", StatementPart.Subject);
        if (defOrigin.sourceStatements() != null) {
            List<PointRange> defRanges = ProvTurtleVisitor.retrieveTurtleLocation(defOrigin.sourceStatements());
            if (!defRanges.isEmpty()) {
                ranges.add(defRanges.getFirst().toLsp());
            }
        }

        // 2. Usage site (object of parent's functionExecution reference)
        if (fe.getParent() instanceof AbstractPlanNode parent) {
            Origin usageOrigin = parent.emitOrigin("functionExecution", StatementPart.Object);
            if (usageOrigin.sourceStatements() != null) {
                List<PointRange> usageRanges = ProvTurtleVisitor.retrieveTurtleLocation(usageOrigin.sourceStatements());
                if (!usageRanges.isEmpty()) {
                    ranges.add(usageRanges.getFirst().toLsp());
                }
            }
        }

        if (ranges.isEmpty()) {
            Range r = PlanNodeDescriptor.getFirstRangeForNode(fe);
            if (r == null && fe.functionMap != null) {
                r = PlanNodeDescriptor.getFirstRangeForNode(fe.functionMap);
            }
            if (r != null) {
                ranges.add(r);
            }
        }

        return ranges;
    }

    private static Command getCommand(AbstractLogicalSource ls, String iriStr, String mappingFileUri) {
        Map<String, String> selector = Map.of(
                "type", "iri",
                "iri", iriStr,
                "mappingFileUri", mappingFileUri
        );

        String label = (ls instanceof LogicalView) ? "$(table) View Logical View Data" : "$(table) View Logical Source Data";
        return new Command(
                label,
                "rml-dev-server.viewSourceData",
                List.of(selector)
        );
    }
}
