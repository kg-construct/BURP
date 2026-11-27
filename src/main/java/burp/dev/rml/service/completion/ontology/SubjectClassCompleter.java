package burp.dev.rml.service.completion.ontology;

import burp.dev.rml.service.completion.CompletionContext;
import burp.dev.rml.service.completion.CompletionContributor;
import burp.model.*;
import org.eclipse.lsp4j.CompletionItem;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/// Autocompletes ontology classes (`rml:class`).
@NullMarked
public class SubjectClassCompleter implements CompletionContributor {

    private final OntologyCompleter ontologyCompleter;

    public SubjectClassCompleter(OntologyCompleter ontologyCompleter) {
        this.ontologyCompleter = ontologyCompleter;
    }

    @Override
    public boolean isApplicable(CompletionContext ctx) {
        if (ctx.hasAncestor(SubjectMap.class)) {
            String clause = ctx.currentClauseText();
            if (clause.contains("class")) return true;
        }
        if (ctx.hasAnyAncestor(BaseObjectMap.class, PredicateMap.class, AbstractLogicalSource.class, JoinCondition.class)) {
            return false;
        }
        String clause = ctx.currentClauseText();
        return clause.contains("rml:class") || clause.contains("rr:class") || clause.contains(":class");
    }

    @Override
    public List<CompletionItem> complete(CompletionContext ctx) {
        var ontRegistry = ctx.workspace().getOntologyRegistry(ctx.loadedMapping().model(), ctx.uri());
        return ontologyCompleter.completeClasses(ontRegistry, ctx.prefixMap(), ctx.effectivePrefix(), ctx.replaceRange());
    }

    @Override
    public int priority() {
        return 10;
    }
}
