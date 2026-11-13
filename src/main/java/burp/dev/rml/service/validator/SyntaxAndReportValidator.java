package burp.dev.rml.service.validator;

import burp.parse.turtleprov.Point;
import burp.parse.turtleprov.ProvTurtleVisitor;
import burp.reporting.Origin;
import burp.reporting.PointRange;
import burp.reporting.RmlError;
import burp.reporting.RmlExecutionReport;
import burp.vocabularies.RER;
import org.eclipse.lsp4j.DiagnosticSeverity;
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
    public ValidationExecutionPolicy getExecutionPolicy() {
        return ValidationExecutionPolicy.IMMEDIATE;
    }

    @Override
    public List<RmlDiagnostic> validate(ValidationContext context) {
        List<RmlDiagnostic> diagnostics = new ArrayList<>();
        RmlExecutionReport report = context.loaded().report();
        if (report.getErrors() == null) {
            return diagnostics;
        }

        for (RmlError error : report.getErrors()) {
            Range range = resolveOriginRange(error.origin());
            DiagnosticSeverity severity = DiagnosticSeverity.Error;
            String code = error.errorType().getURI() != null
                    ? error.errorType().getURI()
                    : RER.MappingError.getURI();

            diagnostics.add(new RmlDiagnostic(
                    range,
                    severity,
                    code,
                    error.message(),
                    "BURP Parser"
            ));
        }

        return diagnostics;
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
