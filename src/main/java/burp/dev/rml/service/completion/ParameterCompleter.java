package burp.dev.rml.service.completion;

import burp.model.Input;
import burp.model.ParameterMap;
import org.eclipse.lsp4j.CompletionItem;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/// Autocompletes FnO execution parameters (`rml:parameter`).
@NullMarked
public class ParameterCompleter implements CompletionContributor {

    private final FunctionCompleter functionCompleter;

    public ParameterCompleter(FunctionCompleter functionCompleter) {
        this.functionCompleter = functionCompleter;
    }

    @Override
    public boolean isApplicable(CompletionContext ctx) {
        if (ctx.hasAncestor(ParameterMap.class) || ctx.hasAncestor(Input.class)) {
            return true;
        }
        return ctx.currentClauseText().contains("parameter");
    }

    @Override
    public List<CompletionItem> complete(CompletionContext ctx) {
        return functionCompleter.completeParameters(ctx);
    }

    @Override
    public int priority() {
        return 0;
    }
}
