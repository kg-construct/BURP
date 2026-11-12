package burp.dev.rml.service.completion;

import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.completion.ontology.DatatypeCompleter;
import burp.dev.rml.service.completion.ontology.OntologyCompleter;
import burp.dev.rml.service.completion.ontology.PredicateCompleter;
import burp.dev.rml.service.completion.ontology.SubjectClassCompleter;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/// Orchestrates autocompletion across specialized [CompletionContributor] implementations:
/// - [ParameterCompleter] for FnO execution parameters.
/// - [SubjectClassCompleter] for OWL/RDFS classes inside `rml:class`.
/// - [ParentTriplesMapCompleter] for target TriplesMap IRIs in `rml:parentTriplesMap`.
/// - [DatatypeCompleter] for property-compatible datatypes in `rml:datatype`.
/// - [ReferenceCompleter] for logical source schema references.
/// - [FunctionCompleter] for FnO functions in `rml:function` / `fno:executes`.
/// - [PredicateCompleter] for ontology predicates in `rml:predicate`.
///
/// ### Completion Priority Table
/// | Priority | Category | When Emitted |
/// | :--- | :--- | :--- |
/// | `00` | Current `fno:Parameter` | Inside `rml:parameter` of a known function execution |
/// | `10` | Class | Ontology classes inside `rml:class` |
/// | `15` | Parent TriplesMap | Sibling TriplesMap IRIs inside `rml:parentTriplesMap` |
/// | `20` | Datatype | Expected datatypes inside `rml:datatype` matching property range |
/// | `30` | Logical Source Reference | Schema fields / columns (`$.field`, `$(column)`) |
/// | `40` | `fno:Function` | Function IRIs at `rml:function` or `fno:executes` |
/// | `50` | Ontology Predicate | Properties matching subject domain or broader ontology terms |
@NullMarked
public class MappingCompleter {

    public static final int MAX_PROPOSITIONS = 250;

    private final ReferenceCompleter referenceCompleter = new ReferenceCompleter();
    private final FunctionCompleter functionCompleter = new FunctionCompleter();
    private final OntologyCompleter ontologyCompleter = new OntologyCompleter();
    private final CompletionContextResolver contextResolver = new CompletionContextResolver();

    private final List<CompletionContributor> completers;

    public MappingCompleter() {
        this(Collections.emptyList());
    }

    public MappingCompleter(List<CompletionContributor> customCompleters) {
        if (customCompleters.isEmpty()) {
            this.completers = List.of(
                    new ParameterCompleter(functionCompleter),
                    new SubjectClassCompleter(ontologyCompleter),
                    new ParentTriplesMapCompleter(),
                    new DatatypeCompleter(ontologyCompleter),
                    referenceCompleter,
                    functionCompleter,
                    new PredicateCompleter(ontologyCompleter)
            );
        } else {
            this.completers = new ArrayList<>(customCompleters);
        }
    }

    public ReferenceCompleter getReferenceCompleter() {
        return referenceCompleter;
    }

    public FunctionCompleter getFunctionCompleter() {
        return functionCompleter;
    }

    public OntologyCompleter getOntologyCompleter() {
        return ontologyCompleter;
    }

    public CompletionContextResolver getContextResolver() {
        return contextResolver;
    }

    public List<CompletionContributor> getCompleters() {
        return completers;
    }

    /// Dispatches an LSP `textDocument/completion` request across all specialized completers
    /// and aggregates the results with deterministic priority scoring.
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> complete(
            @Nullable CompletionParams params,
            MappingWorkspace workspace
    ) {
        CompletionContext ctx = contextResolver.buildContext(params, workspace);
        if (ctx == null) {
            return CompletableFuture.completedFuture(Either.forRight(new CompletionList(false, Collections.emptyList())));
        }

        List<CompletionItem> allItems = new ArrayList<>();
        for (CompletionContributor completer : completers) {
            if (completer.isApplicable(ctx)) {
                allItems.addAll(assignPriority(completer.complete(ctx), completer.priority()));
            }
        }

        // Deduplicate by label (preserving earlier higher-priority items)
        Map<String, CompletionItem> uniqueItems = new LinkedHashMap<>();
        for (CompletionItem item : allItems) {
            uniqueItems.putIfAbsent(item.getLabel(), item);
        }

        List<CompletionItem> finalItems = uniqueItems.values().stream()
                .sorted(Comparator.comparing(CompletionItem::getSortText, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(MAX_PROPOSITIONS)
                .toList();

        return CompletableFuture.completedFuture(Either.forRight(new CompletionList(false, finalItems)));
    }

    private List<CompletionItem> assignPriority(List<CompletionItem> items, int priority) {
        for (int i = 0; i < items.size(); i++) {
            CompletionItem item = items.get(i);
            String existingSort = item.getSortText() != null ? item.getSortText() : String.format("%04d", i);
            item.setSortText(String.format("%02d_%s", priority, existingSort));
        }
        return items;
    }
}
