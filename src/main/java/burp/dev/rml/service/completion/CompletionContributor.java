package burp.dev.rml.service.completion;

import org.eclipse.lsp4j.CompletionItem;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/// Contract for a specialized autocompletion provider.
/// Evaluates its own relevance against the resolved [CompletionContext] before producing candidate items.
@NullMarked
public interface CompletionContributor {

    /// Fast relevance guard: determines whether this completer applies to the current cursor / AST state.
    boolean isApplicable(CompletionContext ctx);

    /// Generates candidate completion items for the resolved context.
    List<CompletionItem> complete(CompletionContext ctx);

    /// Default priority ranking for ordering propositions across completers.
    int priority();
}
