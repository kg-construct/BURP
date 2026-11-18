package burp.dev.rml.service.eval;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Data context specifying a list of specific indices or an iteration index range. Iteration indices are 0-based.
 *
 * @param startIndex The starting index of the iteration range.
 * @param endIndex The ending index of the iteration range.
 * @param specificIndices A list of specific indices to evaluate.
 *                        When specified, it takes precedence over startIndex and endIndex.
 */
public record RangeDataContext(
        int startIndex,
        int endIndex,
        @Nullable List<Integer> specificIndices
) implements EvaluationDataContext {
    public RangeDataContext(int startIndex, int endIndex) {
        this(startIndex, endIndex, null);
    }
}
