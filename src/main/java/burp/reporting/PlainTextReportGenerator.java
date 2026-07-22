package burp.reporting;

import burp.Main;
import burp.parse.turtleprov.ProvTurtleVisitor;
import burp.vocabularies.RER;
import org.apache.jena.ontology.OntClass;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PlainTextReportGenerator {

    public static String generateTextReport(RmlExecutionReport report) {
        StringBuilder sb = new StringBuilder();

        List<RmlError> errors = new ArrayList<>();
        List<RmlError> warnings = new ArrayList<>();
        List<RmlError> info = new ArrayList<>();

        for (RmlError issue : report.getErrors()) {
            OntClass logType = issue.getErrorType();
            if (isOrParent(logType, RER.Information)) info.add(issue);
            else if (isOrParent(logType, RER.Warning)) warnings.add(issue);
            else errors.add(issue);
        }

        printIssues(sb, errors, "Errors", Severity.ERROR);
        printIssues(sb, warnings, "Warnings", Severity.WARNING);
        printIssues(sb, info, "Information", Severity.INFORMATION);

        sb.append("Statistics:\n");
        int triplesMapsSize = 0;
        if (report.getExecutionPlan() != null && report.getExecutionPlan().triplesMaps != null) {
            triplesMapsSize = report.getExecutionPlan().triplesMaps.size();
        }
        sb.append("  - Number of triples maps: ").append(triplesMapsSize).append("\n");
        sb.append("  - Generated statements: ").append(report.getStatistics().getGeneratedStatements()).append("\n");
        sb.append("  - Generated statements per triples map:\n");

        if (report.getStatistics().getGeneratedStatementPerTriplesMap() != null) {
            for (var entry : report.getStatistics().getGeneratedStatementPerTriplesMap().entrySet()) {
                sb.append("      * ")
                  .append(entry.getKey().subject)
                  .append("\t ")
                  .append(entry.getValue())
                  .append("\n");
            }
        }

        return sb.toString();
    }

    private static boolean isOrParent(OntClass ontClass, OntClass parent) {
        return ontClass.equals(parent) || ontClass.hasSuperClass(ontClass);
    }

    private static void printTracingInfo(StringBuilder sb, RmlError issue, Severity severity) {
        if (issue.getOrigin() != null) {
            Origin origin = issue.getOrigin();
            sb.append(prependIndent("In mapping", 4)).append(System.lineSeparator());
            
            Path file = Main.mappingFile != null ? Main.mappingFile.normalize() : null;
            List<PointRange> locations = new ArrayList<>();
            if (origin.sourceStatements() != null) {
                for (RDFPointer ptr : origin.sourceStatements()) {
                    if (ptr instanceof TextFilePointer tfp) {
                        locations.add(tfp.range());
                        if (tfp.path() != null) {
                            file = tfp.path().normalize();
                        }
                    }
                }
            }
            
            locations.addAll(ProvTurtleVisitor.retrieveTurtleLocation(origin.sourceStatements() != null ? origin.sourceStatements() : Collections.emptyList()));

            if (file == null) {
                return;
            }

            // Print file:line:col - line:col
            List<PointRange> sortedLocations = new ArrayList<>(locations);
            sortedLocations.removeIf(l -> l == null || l.start() == null);
            sortedLocations.sort(Comparator.comparing(PointRange::start));

            List<String> lineLocations = new ArrayList<>();
            for (PointRange location : sortedLocations) {
                lineLocations.add(prependIndent(Errors.fileLocationString(file, location), 6));
            }

            // Print File Underlined extract
            String highlight = FileHighlight.extractAndHighlight(file, locations, severity);
            if (highlight != null) {
                if (!lineLocations.isEmpty()) {
                    sb.append(lineLocations.getFirst()).append(System.lineSeparator());
                }
                sb.append(highlight);
            } else {
                for (String lineLoc : lineLocations) {
                    sb.append(lineLoc).append(System.lineSeparator());
                }
            }
        }
    }

    private static void printIssues(StringBuilder sb, List<RmlError> issues, String header, Severity severity) {
        if (issues != null && !issues.isEmpty()) {
            sb.append(header).append(":").append(System.lineSeparator());
            for (int i = 0; i < issues.size(); i++) {
                RmlError issue = issues.get(i);
                String msg = issue.getMessage();
                String msgColored;
                switch (severity) {
                    case WARNING -> msgColored = FileHighlight.ansiYellow(msg);
                    case INFORMATION -> msgColored = FileHighlight.ansiCyan(msg);
                    case null, default -> msgColored = FileHighlight.ansiRed(msg);
                }
                sb.append(prependIndent(msgColored, 2)).append(System.lineSeparator());
                printTracingInfo(sb, issue, severity);
                appendErrorTypeHelp(sb, issue.getErrorType());

                if (issue.getException() != null) {
                    sb.append(prependIndent("Exception: " + issue.getException(), 4)).append(System.lineSeparator());
                    sb.append(prependIndent("StackTrace: " + getStackTraceAsString(issue.getException()), 4)).append(System.lineSeparator());
                }
                // Add empty line between issues for better readability
                if (i < issues.size() - 1) {
                    sb.append(System.lineSeparator());
                }
            }
            sb.append(System.lineSeparator()); // Add an extra line after a section
        }
    }

    private static void appendErrorTypeHelp(StringBuilder sb, OntClass issueType) {
        if (issueType == null) return;
        String label = issueType.getLabel(null);
        sb.append(prependIndent("is a " + label + " (" + issueType + ")", 4)).append(System.lineSeparator());
        
        String comment = issueType.getComment(null);
        if (comment == null) {
            comment = "null";
        }
        sb.append(prependIndent(prependIndent(comment, "| "), 6)).append(System.lineSeparator());
        
        var potentialSolution = issueType.getPropertyValue(RER.potentialSolution);
        if (potentialSolution != null) {
            sb.append(prependIndent("| Try to: " + potentialSolution, 6)).append(System.lineSeparator());
        }
    }

    private static String prependIndent(String s, int indent) {
        return prependIndent(s, " ".repeat(indent));
    }

    private static String prependIndent(String s, String indent) {
        if (s == null) return "";
        String[] lines = s.split("\r?\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append(System.lineSeparator());
            }
            if (!lines[i].isBlank()) {
                sb.append(indent).append(lines[i]);
            } else {
                sb.append(lines[i]);
            }
        }
        return sb.toString();
    }

    private static String getStackTraceAsString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
