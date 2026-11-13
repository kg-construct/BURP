package burp.dev.rml.service.completion;

import burp.model.AbstractLogicalSource;
import burp.model.PlanNode;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;

import burp.model.*;

/// Handles standard LSP `textDocument/completion` requests strictly for
/// logical source data references (CSV, JSON, JSON Schemas, XML, SQL, LogicalViews, and Joins).
@NullMarked
public class ReferenceCompleter implements CompletionContributor {

    @Override
    public boolean isApplicable(CompletionContext ctx) {
        if (ctx.hasAnyAncestor(PredicateMap.class, DatatypeMap.class)) {
            return false;
        }
        if (ctx.hasAncestor(ReferencingObjectMap.class) && !ctx.hasAnyAncestor(ChildMap.class, ParentMap.class)) {
            return false;
        }
        String clause = ctx.currentClauseText();
        if (clause.contains("predicate") || clause.contains("class") || clause.contains("datatype") || clause.contains("parentTriplesMap")) {
            return false;
        }
        return ctx.hasAncestor(Reference.class) || ctx.hasAncestor(RawReference.class) || ctx.hasAncestor(ExpressionMap.class);
    }

    @Override
    public List<CompletionItem> complete(CompletionContext ctx) {
        PlanNode targetNode = ctx.targetNode();
        if (targetNode == null) {
            return Collections.emptyList();
        }
        return completeLogicalSourceReferences(
                targetNode,
                ctx.effectivePrefix(),
                ctx.replaceRange(),
                ctx.workspace().getIterationSampleLimit()
        );
    }

    @Override
    public int priority() {
        return 30;
    }

    /// Resolves and formats candidate data reference completion items from contextual schemas.
    public List<CompletionItem> completeLogicalSourceReferences(
            PlanNode targetNode,
            String effectivePrefix,
            @Nullable Range targetRange,
            @Nullable Integer iterationSampleLimit
    ) {
        AbstractLogicalSource ls = ReferenceSchemaResolver.resolveContextualLogicalSource(targetNode);
        if (ls == null) {
            return Collections.emptyList();
        }
        ReferenceSchemaResolver.SchemaInfo schemaInfo = ReferenceSchemaResolver.extractSchemaInfo(targetNode, ls, iterationSampleLimit);

        List<CompletionItem> results = new ArrayList<>();
        for (var entry : schemaInfo.candidateValues().entrySet()) {
            String fullRef = entry.getKey();
            var exampleVal = entry.getValue();

            boolean matches = effectivePrefix.isEmpty()
                    || fullRef.startsWith(effectivePrefix)
                    || fullRef.toLowerCase().contains(effectivePrefix.toLowerCase())
                    || (fullRef.startsWith("$.") && fullRef.substring(2).startsWith(effectivePrefix));

            if (matches) {
                String shortFormulation = schemaInfo.referenceFormulation().replace("http://w3id.org/rml/", "");
                String detail = "[" + shortFormulation + "]";

                if (!exampleVal.isEmpty()) detail += "\n\nexample: " + exampleVal;

                var origin = schemaInfo.candidateOrigins().get(fullRef);
                if (origin != null) {
                    detail += "\n\nfrom: " + origin.type();
                }

                CompletionItem item = new CompletionItem(fullRef);
                item.setKind(CompletionItemKind.Field);
                item.setDetail(detail);
                item.setInsertText(fullRef);
                if (targetRange != null) {
                    item.setTextEdit(Either.forLeft(new TextEdit(targetRange, fullRef)));
                }
                results.add(item);
            }
        }

        results.sort(Comparator.comparing(CompletionItem::getLabel));
        for (int i = 0; i < results.size(); i++) {
            results.get(i).setSortText(String.format("%04d", i));
        }
        return results;
    }
}
