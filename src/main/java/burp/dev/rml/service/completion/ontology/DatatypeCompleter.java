package burp.dev.rml.service.completion.ontology;

import burp.dev.rml.service.completion.CompletionContext;
import burp.dev.rml.service.completion.CompletionContributor;
import burp.model.*;
import org.apache.jena.rdf.model.Resource;
import org.eclipse.lsp4j.CompletionItem;
import org.jspecify.annotations.NullMarked;

import java.util.Collections;
import java.util.List;

/// Autocompletes expected datatypes for the associated property (`rml:datatype`).
@NullMarked
public class DatatypeCompleter implements CompletionContributor {

    private final OntologyCompleter ontologyCompleter;

    public DatatypeCompleter(OntologyCompleter ontologyCompleter) {
        this.ontologyCompleter = ontologyCompleter;
    }

    @Override
    public boolean isApplicable(CompletionContext ctx) {
        if (ctx.hasAncestor(DatatypeMap.class)) {
            return true;
        }
        if (ctx.hasAnyAncestor(PredicateMap.class, SubjectMap.class, AbstractLogicalSource.class, JoinCondition.class)) {
            return false;
        }
        return ctx.currentClauseText().contains("datatype");
    }

    @Override
    public List<CompletionItem> complete(CompletionContext ctx) {
        Resource pred = ctx.findAssociatedPredicate();
        if (pred == null) {
            return Collections.emptyList();
        }
        var ontRegistry = ctx.workspace().getOntologyRegistry(ctx.loadedMapping().model(), ctx.uri());
        return ontologyCompleter.completeDatatypes(pred, ontRegistry, ctx.prefixMap(), ctx.effectivePrefix(), ctx.replaceRange());
    }

    @Override
    public int priority() {
        return 20;
    }
}
