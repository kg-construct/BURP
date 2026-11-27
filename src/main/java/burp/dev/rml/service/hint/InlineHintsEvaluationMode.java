package burp.dev.rml.service.hint;

import com.google.gson.annotations.SerializedName;
import org.jspecify.annotations.NullMarked;

/// Evaluation strategy for editor inline hints.
@NullMarked
public enum InlineHintsEvaluationMode {
    @SerializedName("independentEvaluation")
    INDEPENDENT_EVALUATION,

    @SerializedName("trace")
    TRACE
}
