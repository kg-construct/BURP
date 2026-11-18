package burp.dev.rml.service.completion;

import burp.dev.rml.service.RmlEvaluationResultFormatter;
import burp.model.JoinCondition;
import burp.model.ReferencingObjectMap;
import burp.model.TriplesMap;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/// Autocompletes target TriplesMap identifiers for `rml:parentTriplesMap`.
@NullMarked
public class ParentTriplesMapCompleter implements CompletionContributor {

    @Override
    public boolean isApplicable(CompletionContext ctx) {
        if (ctx.hasAncestor(ReferencingObjectMap.class)) {
            return !ctx.hasAncestor(JoinCondition.class);
        }
        return ctx.currentClauseText().contains("parentTriplesMap");
    }

    @Override
    public List<CompletionItem> complete(CompletionContext ctx) {
        List<CompletionItem> items = new ArrayList<>();
        TriplesMap selfTm = ctx.findEnclosingTriplesMap();

        for (TriplesMap tm : ctx.loadedMapping().document().triplesMaps) {
            if (tm.subject != null && tm.subject.isURIResource()) {
                String uri = tm.subject.getURI();
                String compact = RmlEvaluationResultFormatter.compactIri(uri, ctx.prefixMap());
                if (!ctx.effectivePrefix().isEmpty() && !compact.toLowerCase().contains(ctx.effectivePrefix().toLowerCase())) {
                    continue;
                }
                CompletionItem item = new CompletionItem(compact);
                item.setKind(CompletionItemKind.Reference);
                item.setDetail("TriplesMap" + (tm == selfTm ? " (self)" : ""));
                item.setInsertText(compact);
                if (ctx.replaceRange() != null) {
                    item.setTextEdit(Either.forLeft(new TextEdit(ctx.replaceRange(), compact)));
                }
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public int priority() {
        return 15;
    }
}
