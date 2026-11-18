package burp.model.join;

import burp.model.Iteration;
import burp.model.JoinCondition;
import burp.model.context.EvaluationContext;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.jspecify.annotations.NullMarked;

import java.util.*;

/**
 * High-performance in-memory hash index for parent iterations.
 * Converts $O(N \times M)$ nested loop evaluation into $O(N + M)$ hash lookups.
 */
@NullMarked
public class ParentJoinIndex {

    private final boolean unconditional;
    private final List<Iteration> allParentIterations;
    private final Multimap<CompositeJoinKey, Iteration> index;

    public record CompositeJoinKey(List<CanonicalJoinKey> keys) {
        public static CompositeJoinKey of(List<Object> rawValues) {
            List<CanonicalJoinKey> canonical = new ArrayList<>(rawValues.size());
            for (Object v : rawValues) {
                canonical.add(CanonicalJoinKey.of(v));
            }
            return new CompositeJoinKey(canonical);
        }
    }

    private ParentJoinIndex(boolean unconditional, List<Iteration> allParentIterations, Multimap<CompositeJoinKey, Iteration> index) {
        this.unconditional = unconditional;
        this.allParentIterations = allParentIterations;
        this.index = index;
    }

    /**
     * Builds a ParentJoinIndex from the provided parent iterations and join conditions.
     */
    public static ParentJoinIndex build(
            Iterable<Iteration> parentIterations,
            List<JoinCondition> joinConditions,
            EvaluationContext unsafeParentCtx
    ) {
        List<Iteration> parentList = new ArrayList<>();
        for (Iteration iter : parentIterations) {
            parentList.add(iter);
        }

        if (joinConditions.isEmpty()) {
            return new ParentJoinIndex(true, parentList, ArrayListMultimap.create());
        }

        Multimap<CompositeJoinKey, Iteration> map = ArrayListMultimap.create();

        for (Iteration pIter : parentList) {
            List<List<Object>> conditionValues = new ArrayList<>(joinConditions.size());
            boolean hasMissingKey = false;

            for (JoinCondition jc : joinConditions) {
                if (jc.parentMap == null) {
                    hasMissingKey = true;
                    break;
                }
                List<Object> vals = jc.parentMap.generateValues(pIter, unsafeParentCtx);
                if (vals.isEmpty()) {
                    hasMissingKey = true;
                    break;
                }
                conditionValues.add(vals);
            }

            if (hasMissingKey) {
                continue;
            }

            // Generate Cartesian product of multiple parent values per join condition if multivalued
            List<List<Object>> product = cartesianProduct(conditionValues);
            for (List<Object> tuple : product) {
                map.put(CompositeJoinKey.of(tuple), pIter);
            }
        }

        return new ParentJoinIndex(false, parentList, map);
    }

    /**
     * Finds matching parent iterations for a child iteration.
     */
    public List<Iteration> findMatches(
            Iteration childIter,
            List<JoinCondition> joinConditions,
            EvaluationContext unsafeChildCtx
    ) {
        if (unconditional) {
            return allParentIterations;
        }
        if (joinConditions.isEmpty()) {
            return allParentIterations;
        }

        List<List<Object>> childConditionValues = new ArrayList<>(joinConditions.size());
        for (JoinCondition jc : joinConditions) {
            if (jc.childMap == null) {
                return Collections.emptyList();
            }
            List<Object> vals = jc.childMap.generateValues(childIter, unsafeChildCtx);
            if (vals.isEmpty()) {
                return Collections.emptyList();
            }
            childConditionValues.add(vals);
        }

        List<List<Object>> childTuples = cartesianProduct(childConditionValues);
        Set<Iteration> matched = new LinkedHashSet<>();

        for (List<Object> tuple : childTuples) {
            CompositeJoinKey searchKey = CompositeJoinKey.of(tuple);
            Collection<Iteration> found = index.get(searchKey);
            matched.addAll(found);
        }

        return new ArrayList<>(matched);
    }

    private static List<List<Object>> cartesianProduct(List<List<Object>> lists) {
        List<List<Object>> result = new ArrayList<>();
        cartesianHelper(lists, 0, new ArrayList<>(), result);
        return result;
    }

    private static void cartesianHelper(List<List<Object>> lists, int depth, List<Object> current, List<List<Object>> result) {
        if (depth == lists.size()) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (Object item : lists.get(depth)) {
            current.add(item);
            cartesianHelper(lists, depth + 1, current, result);
            current.removeLast();
        }
    }
}
