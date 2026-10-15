package burp.dev.rml.service.source;

import burp.dev.rml.service.plan.PlanNodeDescriptor;
import burp.dev.rml.service.MappingWorkspace;
import burp.model.AbstractLogicalSource;
import burp.model.PlanNode;
import burp.model.lv.LogicalView;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Service providing standard LSP CodeLenses (textDocument/codeLens)
 * for actionable RML constructs, such as previewing Logical Views.
 */
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

            var codeLenses = document.descendantsAndReferences(AbstractLogicalSource.class).map(ls -> {
                Range nodeRange = PlanNodeDescriptor.getFirstRangeForNode(ls);
                if (nodeRange != null && nodeRange.getStart() != null) {
                    Range lensRange = new Range(nodeRange.getStart(), nodeRange.getStart());
                    String iriStr = PlanNode.resourceToIriOrBNode(ls.getIri());
                    Command command = getCommand(ls, iriStr, mappingFileUri);
                    return new CodeLens(lensRange, command, null);
                }
                return null;
            }).filter(Objects::nonNull).toList();
            return CompletableFuture.completedFuture(codeLenses);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
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
