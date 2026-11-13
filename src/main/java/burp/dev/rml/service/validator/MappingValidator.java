package burp.dev.rml.service.validator;

import burp.model.AbstractPlanNode;
import burp.model.PlanNode;
import burp.reporting.Origin;
import burp.reporting.PointRange;
import burp.reporting.RmlError;
import burp.reporting.StatementPart;
import burp.reporting.TextFilePointer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * Common contract for independent mapping validation rules.
 */
@NullMarked
public interface MappingValidator {

    /**
     * Unique stable identifier for this validator (used for granular caching and logging).
     * Examples: "syntax", "fno", "unused-elements", "ontology-constants", "data-references", "runtime-ontology".
     */
    String getId();

    /**
     * Human-readable source label reported on diagnostics produced by this validator.
     */
    default String getSourceName() {
        return "BURP Validator";
    }

    /**
     * Declares whether this validator runs immediately on every keystroke or is debounced in the background.
     */
    ValidationExecutionPolicy getExecutionPolicy();

    /**
     * Executes validation against the parsed mapping context.
     *
     * @param context the loaded mapping, file URI, and workspace coordinator
     * @return list of detected errors/warnings (located or unlocated)
     */
    List<RmlError> validate(ValidationContext context);

    static @Nullable Origin resolveNodeOrigin(PlanNode node, String propertyName) {
        if (node instanceof AbstractPlanNode apn) {
            Origin origin = apn.emitOrigin(propertyName, StatementPart.Object);
            if (!origin.ranges().isEmpty()) {
                return origin;
            }
        }
        return firstNodeRangeOrigin(node);
    }

    static @Nullable Origin firstNodeRangeOrigin(@Nullable PlanNode node) {
        if (node == null) {
            return null;
        }
        PointRange first = node.nodeRanges().stream().sorted().findFirst().orElse(null);
        return first != null ? new Origin(List.of(new TextFilePointer(Path.of(""), first))) : null;
    }
}
