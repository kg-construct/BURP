package burp.dev.rml.service.hint;

import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.eval.InlineEvaluateItem;
import burp.dev.rml.service.eval.RangeDataContext;
import burp.dev.rml.service.eval.InlineEvaluator;
import burp.dev.rml.service.eval.RmlEvaluator;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/// Service providing standard LSP Inlay Hints (textDocument/inlayHint).
///
///  - Non-constant inline evaluation hints (by calling [InlineEvaluator])
///  - Functions execution parameter names and type (by calling [ParameterHintProvider])
///  - Parent/child source hints (by calling [JoinSourceHintProvider])
///
@NullMarked
public class LspInlineHintProvider {

    private final InlineEvaluator inlineEvaluator = new InlineEvaluator();
    private final JoinSourceHintProvider joinSourceHintProvider = new JoinSourceHintProvider();
    private final ParameterHintProvider parameterHintProvider = new ParameterHintProvider();

    public CompletableFuture<List<InlayHint>> provideInlayHints(
            @Nullable InlayHintParams params,
            MappingWorkspace workspace,
            RmlEvaluator evaluator
    ) {
        if (params == null || params.getTextDocument() == null || params.getTextDocument().getUri() == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        try {
            String mappingFileUri = params.getTextDocument().getUri();
            var mappingDocument = workspace.loadMappingWithStableIds(mappingFileUri);
            if (mappingDocument.triplesMaps.isEmpty()) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }

            Map<String, String> prefixMap = workspace.getPrefixMap(mappingFileUri);
            List<InlayHint> hints = new ArrayList<>();
            Set<String> seenPositions = new HashSet<>();

            // Static JoinCondition Source Hints (rml:child / rml:parent sources)
            addHints(hints, joinSourceHintProvider.extractJoinHints(mappingDocument, prefixMap), seenPositions);

            // Static Parameter Hints (FnO parameter names & types)
            addHints(hints, parameterHintProvider.extractInlayHints(mappingDocument, prefixMap), seenPositions);

            if (workspace.isInlineHintsEvaluationEnabled()) {
                List<InlineEvaluateItem> items = inlineEvaluator.evaluateItems(mappingDocument, new RangeDataContext(0, 1), null, evaluator, workspace.getBaseIri(), prefixMap);
                addHints(hints, extractEvaluationHints(items), seenPositions);
            }

            return CompletableFuture.completedFuture(hints);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
    }

    private void addHints(List<InlayHint> target, List<InlayHint> source, Set<String> seenPositions) {
        for (InlayHint hint : source) {
            if (hint.getPosition() != null) {
                String labelStr = hint.getLabel().isLeft() ? hint.getLabel().getLeft() : "";
                String key = hint.getPosition().getLine() + ":" + hint.getPosition().getCharacter() + ":" + labelStr;
                if (seenPositions.add(key)) {
                    target.add(hint);
                }
            }
        }
    }

    private List<InlayHint> extractEvaluationHints(List<InlineEvaluateItem> items) {
        List<InlayHint> hints = new ArrayList<>();
        for (InlineEvaluateItem item : items) {
            Range range = item.originRange();
            if (range.getEnd() == null || item.formattedValues().isEmpty()) {
                continue;
            }

            String display = item.formattedValues().getFirst();
            String rawDisplay = !item.rawFormattedValues().isEmpty() ? item.rawFormattedValues().getFirst() : null;

            InlayHint hint = new InlayHint();
            hint.setPosition(range.getEnd());
            hint.setLabel(Either.forLeft(" ⇾ " + display));
            hint.setKind(InlayHintKind.Type);

            StringBuilder sb = new StringBuilder();
            sb.append("**").append(item.type()).append(" Evaluation** (`").append(item.id()).append("`):\n```turtle\n")
                    .append(display).append("\n```");
            if (rawDisplay != null && !rawDisplay.equals(display)) {
                sb.append("\n*Full Term*: `").append(rawDisplay).append("`");
            }
            hint.setTooltip(Either.forRight(new MarkupContent("markdown", sb.toString())));

            hints.add(hint);
        }
        return hints;
    }
}
