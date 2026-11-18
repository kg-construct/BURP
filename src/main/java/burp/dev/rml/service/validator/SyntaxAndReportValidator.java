package burp.dev.rml.service.validator;

import burp.parse.turtleprov.Point;
import burp.parse.turtleprov.ProvTurtleVisitor;
import burp.reporting.Origin;
import burp.reporting.PointRange;
import burp.reporting.RmlError;
import burp.reporting.RmlExecutionReport;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates SHACL shape compliance and Turtle parser syntax errors extracted from the mapping execution report.
 */
@NullMarked
public class SyntaxAndReportValidator implements MappingValidator {

    public static final String ID = "syntax";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getSourceName() {
        return "BURP Parser";
    }

    @Override
    public ValidationExecutionPolicy getExecutionPolicy() {
        return ValidationExecutionPolicy.IMMEDIATE;
    }

    @Override
    public List<RmlError> validate(ValidationContext context) {
        RmlExecutionReport report = context.loaded().report();
        List<RmlError> results = new ArrayList<>();
        results.addAll(report.getErrors());
        results.addAll(report.getWarnings());
        return results;
    }

    public static @Nullable Range resolveOriginRange(@Nullable Origin origin) {
        if (origin == null || origin.sourceStatements() == null) {
            return null;
        }
        List<PointRange> ranges = ProvTurtleVisitor.retrieveTurtleLocation(origin.sourceStatements());
        if (ranges.isEmpty()) {
            return null;
        }
        Point start = ranges.getFirst().start();
        Point end = ranges.getLast().end() != null ? ranges.getLast().end() : ranges.getLast().start();
        return new Range(start.toLsp(), end.toLsp());
    }
}
