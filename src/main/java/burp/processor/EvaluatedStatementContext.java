package burp.processor;

import burp.model.BaseObjectMap;
import burp.model.OriginLineage;
import burp.model.PredicateMap;
import burp.model.PredicateObjectMap;
import burp.model.RmlEvaluationResult;
import burp.model.TriplesMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Context payload emitted for each evaluated predicate-object pair when a statement observer is active.
@NullMarked
public record EvaluatedStatementContext(
        TriplesMap triplesMap,
        PredicateObjectMap predicateObjectMap,
        @Nullable PredicateMap predicateMap,
        @Nullable BaseObjectMap objectMap,
        RmlEvaluationResult evaluationResult,
        @Nullable OriginLineage originLineage
) {}
