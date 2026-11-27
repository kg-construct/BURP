package burp.dev.rml.service.completion.ontology;

import burp.dev.rml.service.completion.CompletionContext;
import burp.dev.rml.service.completion.CompletionContributor;
import burp.model.*;
import org.eclipse.lsp4j.CompletionItem;
import org.jspecify.annotations.NullMarked;

import java.util.Collections;
import java.util.List;

/// Autocompletes ontology predicates/properties (`rml:predicate`).
@NullMarked
public class PredicateCompleter implements CompletionContributor {

    private final OntologyCompleter ontologyCompleter;

    public PredicateCompleter(OntologyCompleter ontologyCompleter) {
        this.ontologyCompleter = ontologyCompleter;
    }

    @Override
    public boolean isApplicable(CompletionContext ctx) {
        if (ctx.hasAncestor(PredicateMap.class)) {
            return true;
        }
        if (ctx.hasAnyAncestor(BaseObjectMap.class, SubjectMap.class, AbstractLogicalSource.class, JoinCondition.class)) {
            return false;
        }
        return ctx.currentClauseText().contains("predicate");
    }

    @Override
    public List<CompletionItem> complete(CompletionContext ctx) {
        TriplesMap tm = ctx.findEnclosingTriplesMap();
        if (tm == null) {
            return Collections.emptyList();
        }
        var ontRegistry = ctx.workspace().getOntologyRegistry(ctx.loadedMapping().model(), ctx.uri());
        return ontologyCompleter.completePredicates(tm, ontRegistry, ctx.prefixMap(), ctx.effectivePrefix(), ctx.replaceRange());
    }

    @Override
    public int priority() {
        return 50;
    }
}
