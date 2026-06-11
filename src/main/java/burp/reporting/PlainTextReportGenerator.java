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

        printIssues(sb, report.getErrors(), "Errors");

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

    private static void printTracingInfo(StringBuilder sb, RmlError issue) {
        if (issue.getOrigin() != null) {
            Origin origin = issue.getOrigin();
            sb.append(prependIndent("In mapping", 4)).append(System.lineSeparator());
            Path file = Main.mappingFile.normalize();
            List<PointRange> locations = ProvTurtleVisitor.retrieveTurtleLocation(origin.sourceStatements() != null ? origin.sourceStatements() : Collections.emptyList());

            // Print file:line:col - line:col
            List<PointRange> sortedLocations = new ArrayList<>(locations);
            sortedLocations.sort(Comparator.comparing(PointRange::getStart));

            List<String> lineLocations = new ArrayList<>();
            for (PointRange location : sortedLocations) {
                lineLocations.add(prependIndent(Errors.fileLocationString(file, location), 6));
            }

            // Print File Underlined extract
            String highlight = FileHighlight.extractAndHighlight(file, locations);
            if (highlight != null) {
                if (!lineLocations.isEmpty()) {
                    sb.append(lineLocations.get(0)).append(System.lineSeparator());
                }
                sb.append(highlight);
            } else {
                for (String lineLoc : lineLocations) {
                    sb.append(lineLoc).append(System.lineSeparator());
                }
            }
        }
    }

    private static void printIssues(StringBuilder sb, List<RmlError> issues, String header) {
        if (issues != null && !issues.isEmpty()) {
            sb.append(header).append(":").append(System.lineSeparator());
            for (int i = 0; i < issues.size(); i++) {
                RmlError issue = issues.get(i);
                sb.append(prependIndent(FileHighlight.ansiRed(issue.getMessage()), 2)).append(System.lineSeparator());
                printTracingInfo(sb, issue);
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
