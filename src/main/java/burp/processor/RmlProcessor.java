package burp.processor;

import burp.dev.rml.service.selector.StructureSelector;
import burp.dev.rml.service.cache.DevServerCache;
import burp.model.*;
import burp.model.context.EvaluationContext;
import burp.model.join.ParentJoinIndex;
import burp.reporting.BurpException;
import burp.reporting.RmlError;
import burp.reporting.RmlExecutionReport;
import burp.vocabularies.RER;
import org.apache.jena.query.Dataset;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * High-level RML Processor facade for executing RML mappings as a library.
 * Supports returning in-memory multi-graph Jena Datasets per target,
 * Statement lists (graph included) per target, and push-based streaming via BiConsumer.
 */
public class RmlProcessor {

    private final @Nullable DevServerCache cache;

    public RmlProcessor() {
        this(null);
    }

    public RmlProcessor(@Nullable DevServerCache cache) {
        this.cache = cache;
    }

    public @Nullable DevServerCache getCache() {
        return cache;
    }

    /**
     * Executes the mapping document and returns Jena Datasets (multiple graphs) per target.
     */
    public Map<LogicalTarget, Dataset> executeToDatasets(MappingDocument document) {
        return executeToDatasets(document, ExecutionOptions.defaults());
    }

    public Map<LogicalTarget, Dataset> executeToDatasets(MappingDocument document, ExecutionOptions options) {
        Map<LogicalTarget, List<RdfStatement>> statementsByTarget = executeToStatements(document, options);
        Map<LogicalTarget, Dataset> datasets = new LinkedHashMap<>();
        for (Map.Entry<LogicalTarget, List<RdfStatement>> entry : statementsByTarget.entrySet()) {
            datasets.put(entry.getKey(), JenaDatasetGenerator.toDataset(entry.getValue()));
        }
        return datasets;
    }

    /**
     * Executes the mapping document and returns lists of statements (graph included) per target.
     */
    public Map<LogicalTarget, List<RdfStatement>> executeToStatements(MappingDocument document) {
        return executeToStatements(document, ExecutionOptions.defaults());
    }

    public Map<LogicalTarget, List<RdfStatement>> executeToStatements(MappingDocument document, ExecutionOptions options) {
        RmlExecutionReport report = new RmlExecutionReport();
        EvaluationContext context = EvaluationContext.createDefault()
                .withReport(report)
                .withBaseIri(options.baseIri());
        return executeToStatements(document, options, context);
    }

    public Map<LogicalTarget, List<RdfStatement>> executeToStatements(
            MappingDocument document,
            ExecutionOptions options,
            EvaluationContext context
    ) {
        Objects.requireNonNull(document, "MappingDocument must not be null");
        Objects.requireNonNull(options, "ExecutionOptions must not be null");
        Objects.requireNonNull(context, "EvaluationContext must not be null");

        context = configureContextCache(document, options, context);

        List<TriplesMap> executionOrder = determineExecutionOrder(document, options.focusStructure());
        List<RdfStatementLike> allRawStmts = new ArrayList<>();

        for (TriplesMap tm : executionOrder) {
            if (options.cancellationToken() != null) {
                options.cancellationToken().throwIfCancelled();
            }
            if (tm.logicalSource == null) {
                allRawStmts.addAll(tm.generate(null, context));
            } else {
                Iterable<Iteration> iters = context.getIterations(tm.logicalSource);
                for (Iteration iter : iters) {
                    allRawStmts.addAll(tm.generate(iter, context));
                }
            }
        }

        List<RdfStatement> finalizedStmts = document.postProcessContainers(allRawStmts);
        Map<LogicalTarget, List<RdfStatement>> statementsByTarget = new LinkedHashMap<>();

        for (RdfStatement stmt : finalizedStmts) {
            if (stmt == null) continue;
            if (stmt.targets() == null || stmt.targets().isEmpty()) {
                statementsByTarget.computeIfAbsent(LogicalTarget.DEFAULT, k -> new ArrayList<>()).add(stmt);
            } else {
                for (LogicalTarget target : stmt.targets()) {
                    statementsByTarget.computeIfAbsent(target != null ? target : LogicalTarget.DEFAULT, k -> new ArrayList<>()).add(stmt);
                }
            }
        }

        return statementsByTarget;
    }

    /**
     * Push-based streaming execution: emits statements per target as they are generated.
     */
    public void execute(MappingDocument document, BiConsumer<LogicalTarget, RdfStatement> consumer) {
        execute(document, ExecutionOptions.defaults(), consumer);
    }

    public void execute(MappingDocument document, ExecutionOptions options, BiConsumer<LogicalTarget, RdfStatement> consumer) {
        RmlExecutionReport report = new RmlExecutionReport();
        EvaluationContext context = EvaluationContext.createDefault()
                .withReport(report)
                .withBaseIri(options.baseIri());
        execute(document, options, context, consumer);
    }

    public void execute(
            MappingDocument document,
            ExecutionOptions options,
            EvaluationContext context,
            BiConsumer<LogicalTarget, RdfStatement> consumer
    ) {
        Objects.requireNonNull(consumer, "Consumer must not be null");
        Map<LogicalTarget, List<RdfStatement>> byTarget = executeToStatements(document, options, context);
        for (Map.Entry<LogicalTarget, List<RdfStatement>> entry : byTarget.entrySet()) {
            for (RdfStatement stmt : entry.getValue()) {
                consumer.accept(entry.getKey(), stmt);
            }
        }
    }

    private EvaluationContext configureContextCache(
            MappingDocument document,
            ExecutionOptions options,
            EvaluationContext context
    ) {
        // Cache setup if enabled
        if (options.useCache() && cache != null) {
            Map<AbstractLogicalSource, List<Iteration>> sourceIters = new HashMap<>();
            for (TriplesMap tm : document.triplesMaps) {
                if (tm.logicalSource != null && !sourceIters.containsKey(tm.logicalSource)) {
                    List<Iteration> cached = cache.getIterations(tm.logicalSource);
                    if (cached == null) {
                        Iterable<Iteration> loaded = tm.logicalSource.iterator();
                        if (loaded instanceof List<Iteration> list) {
                            cached = list;
                        } else {
                            cached = new ArrayList<>();
                            for (Iteration it : loaded) {
                                cached.add(it);
                            }
                        }
                        cache.putIterations(tm.logicalSource, cached);
                    }
                    sourceIters.put(tm.logicalSource, cached);
                }
            }
            if (!sourceIters.isEmpty()) {
                context = context.withSourceIterations(sourceIters);
            }

            // Pre-populate join indexes from cache or compute
            Map<PlanNode, ParentJoinIndex> joinIndexes = new HashMap<>();
            EvaluationContext unsafeCtx = context.withTemplateSafety(TemplateReferenceSafety.UNSAFE);
            for (TriplesMap tm : document.triplesMaps) {
                for (ReferencingObjectMap rom : tm.descendants(ReferencingObjectMap.class).toList()) {
                    if (rom.parentTriplesMap != null && rom.parentTriplesMap.logicalSource != null) {
                        AbstractLogicalSource pSource = rom.parentTriplesMap.logicalSource;
                        ParentJoinIndex idx = cache.getParentJoinIndex(rom, pSource);
                        if (idx == null) {
                            Iterable<Iteration> pIters = context.getIterations(pSource);
                            idx = ParentJoinIndex.build(pIters, rom.joinConditions, unsafeCtx);
                            cache.putParentJoinIndex(rom, pSource, idx);
                        }
                        joinIndexes.put(rom, idx);
                    }
                }
            }
            if (!joinIndexes.isEmpty()) {
                context = context.withJoinIndexes(joinIndexes);
            }
        }
        return context;
    }

    /**
     * Orders TriplesMaps prioritizing the focused structure and its dependencies first.
     */
    public List<TriplesMap> determineExecutionOrder(MappingDocument document, @Nullable StructureSelector focusStructure) {
        if (focusStructure == null || document.triplesMaps.isEmpty()) {
            return new ArrayList<>(document.triplesMaps);
        }

        TriplesMap focusedTm = findFocusedTriplesMap(document, focusStructure);
        if (focusedTm == null) {
            return new ArrayList<>(document.triplesMaps);
        }

        List<TriplesMap> ordered = new ArrayList<>();
        Set<TriplesMap> visited = new HashSet<>();

        // Add dependencies of the focused TriplesMap (parent TriplesMaps in ReferencingObjectMaps)
        for (ReferencingObjectMap rom : focusedTm.descendants(ReferencingObjectMap.class).toList()) {
            if (rom.parentTriplesMap != null && rom.parentTriplesMap != focusedTm && visited.add(rom.parentTriplesMap)) {
                ordered.add(rom.parentTriplesMap);
            }
        }

        // Add the focused TriplesMap
        if (visited.add(focusedTm)) {
            ordered.add(focusedTm);
        }

        // Add remaining TriplesMaps
        for (TriplesMap tm : document.triplesMaps) {
            if (visited.add(tm)) {
                ordered.add(tm);
            }
        }

        return ordered;
    }

    private @Nullable TriplesMap findFocusedTriplesMap(MappingDocument document, StructureSelector selector) {
        for (TriplesMap tm : document.triplesMaps) {
            if (burp.dev.NodeSelectorMatcher.matches(tm, selector)) {
                return tm;
            }
            for (PlanNode desc : tm.descendants(PlanNode.class).toList()) {
                if (burp.dev.NodeSelectorMatcher.matches(desc, selector)) {
                    return tm;
                }
            }
        }
        return null;
    }
}
