package burp.reporting;

import burp.model.MappingDocument;
import burp.model.TriplesMap;
import burp.parse.turtleprov.ProvTurtleVisitor;
import burp.vocabularies.RER;
import org.apache.jena.ontapi.model.OntClass;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.*;

/// Formats an {@link RmlExecutionReport} into a readable plain-text CLI report.
///
/// - Order first unlocated errors and warnings, then all located logs in order in the file, then unlocated infos.
/// - Aggregate logs sharing the same RER error type and mapping origin (or same plan node and message when unlocated)
///   via {@link RmlExecutionReport#getAggregatedLogs()}, appending `(xN)` when repeated.
///
/// ```text
/// <error|warning|info>: <rer:Type>[ (x<N>)]
/// <message>
///     in <path>:<startLine>:<startCol>[-<endLine>:<endCol>]
/// <source snippet with highlight or ^^^ underline>
/// [stacktrace: <trace>]
/// [<Error|Warning|Information> definition, hints at <rerTypeUri>]
/// Stats: <total> statement(s) from <count> triples map(s)
/// [  <compactTriplesMapIri>: <count>]
/// ```
@NullMarked
public class PlainTextReportGenerator {

    private static final int MAX_STACK_FRAMES = 15;

    private record ResolvedIssue(
            AggregatedRmlError agg,
            @Nullable Path file,
            List<PointRange> sortedLocations
    ) {
        boolean isLocated() {
            return file != null && !sortedLocations.isEmpty();
        }
    }

    public static String generateTextReport(RmlExecutionReport report) {
        return generateTextReport(report, FileHighlight.hasColorSupport(), false);
    }

    public static String generateTextReport(RmlExecutionReport report, boolean useColors) {
        return generateTextReport(report, useColors, false);
    }

    public static String generateTextReport(RmlExecutionReport report, boolean useColors, boolean verbose) {
        StringBuilder sb = new StringBuilder();

        List<ResolvedIssue> ordered = new ArrayList<>();
        for (AggregatedRmlError agg : report.getAggregatedLogs()) {
            ordered.add(resolveIssue(agg));
        }
        ordered.sort(issueOrderComparator());

        Set<String> seenHelpTypes = new HashSet<>();
        for (int i = 0; i < ordered.size(); i++) {
            printIssue(sb, ordered.get(i), useColors, verbose, seenHelpTypes);
            if (i < ordered.size() - 1) {
                sb.append(System.lineSeparator());
            }
        }

        appendStatistics(sb, report);

        return sb.toString();
    }

    private static Comparator<ResolvedIssue> issueOrderComparator() {
        return (a, b) -> {
            int bucketA = orderBucket(a);
            int bucketB = orderBucket(b);
            if (bucketA != bucketB) {
                return Integer.compare(bucketA, bucketB);
            }
            if (bucketA == 0) {
                return Integer.compare(
                        severityRank(a.agg().primary().severity()),
                        severityRank(b.agg().primary().severity())
                );
            }
            if (bucketA == 1) {
                assert a.file() != null && b.file() != null;
                int fileCmp = relativizeToCwd(a.file()).toString()
                        .compareTo(relativizeToCwd(b.file()).toString());
                if (fileCmp != 0) {
                    return fileCmp;
                }
                int locCmp = a.sortedLocations().getFirst().start()
                        .compareTo(b.sortedLocations().getFirst().start());
                if (locCmp != 0) {
                    return locCmp;
                }
                return Integer.compare(
                        severityRank(a.agg().primary().severity()),
                        severityRank(b.agg().primary().severity())
                );
            }
            return 0;
        };
    }

    private static int orderBucket(ResolvedIssue issue) {
        if (issue.isLocated()) {
            return 1;
        }
        return issue.agg().primary().severity() == Severity.INFORMATION ? 2 : 0;
    }

    private static int severityRank(Severity severity) {
        return switch (severity) {
            case ERROR -> 0;
            case WARNING -> 1;
            case INFORMATION -> 2;
        };
    }

    private static ResolvedIssue resolveIssue(AggregatedRmlError agg) {
        Origin origin = agg.primary().origin();
        if (origin == null) {
            return new ResolvedIssue(agg, null, List.of());
        }

        Path originFile = origin.file();
        Path file = originFile != null ? originFile.normalize() : null;
        List<PointRange> locations = new ArrayList<>();
        List<? extends RDFPointer> pointers = origin.sourceStatements();
        if (pointers != null) {
            for (RDFPointer ptr : pointers) {
                if (ptr instanceof TextFilePointer(Path path, PointRange range)) {
                    if (range != null) {
                        locations.add(range);
                    }
                    if (file == null) {
                        file = path.normalize();
                    }
                }
            }
            locations.addAll(ProvTurtleVisitor.retrieveTurtleLocation(pointers));
        }

        if (locations.isEmpty()) {
            return new ResolvedIssue(agg, file, List.of());
        }
        locations.sort(Comparator.comparing(PointRange::start));
        return new ResolvedIssue(agg, file, List.copyOf(locations));
    }

    private static void printIssue(
            StringBuilder sb,
            ResolvedIssue resolved,
            boolean useColors,
            boolean verbose,
            Set<String> seenHelpTypes
    ) {
        AggregatedRmlError agg = resolved.agg();
        RmlError issue = agg.primary();
        Severity severity = issue.severity();
        String prefix = severityPrefix(severity);
        String msgColored = colorText(issue.message(), severity, useColors);
        String prefixColored = colorText(prefix + ":", severity, useColors);

        StringBuilder headerLine = new StringBuilder(prefixColored)
                .append(" ")
                .append(compactErrorType(issue.errorType()));
        if (agg.additionalCount() > 0) {
            headerLine.append(" (x").append(1 + agg.additionalCount()).append(")");
        }
        sb.append(headerLine).append(System.lineSeparator());
        sb.append(msgColored).append(System.lineSeparator());

        printTracingInfo(sb, resolved, severity, useColors);
        appendExceptionInfo(sb, issue, verbose);
        appendErrorTypeHelp(sb, issue.errorType(), severity, seenHelpTypes);
    }

    private static String severityPrefix(Severity severity) {
        return switch (severity) {
            case ERROR -> "error";
            case WARNING -> "warning";
            case INFORMATION -> "info";
        };
    }

    private static String severityLabel(Severity severity) {
        return switch (severity) {
            case ERROR -> "Error";
            case WARNING -> "Warning";
            case INFORMATION -> "Information";
        };
    }

    private static String colorText(String text, Severity severity, boolean useColors) {
        if (!useColors) {
            return text;
        }
        return switch (severity) {
            case WARNING -> FileHighlight.ansiYellow(text);
            case INFORMATION -> FileHighlight.ansiCyan(text);
            case ERROR -> FileHighlight.ansiRed(text);
        };
    }

    private static String compactErrorType(@Nullable OntClass issueType) {
        if (issueType == null) {
            return "rer:Error";
        }
        String uri = issueType.getURI();
        if (uri != null && uri.startsWith(RER.NS)) {
            return "rer:" + uri.substring(RER.NS.length());
        }
        return uri != null ? uri : issueType.toString();
    }

    private static void printTracingInfo(StringBuilder sb, ResolvedIssue resolved, Severity severity, boolean useColors) {
        Path file = resolved.file();
        List<PointRange> sortedLocations = resolved.sortedLocations();
        if (file == null || sortedLocations.isEmpty()) {
            return;
        }

        Path displayPath = relativizeToCwd(file);
        String highlight = FileHighlight.extractAndHighlight(file, sortedLocations, 1, severity, useColors);
        if (highlight != null) {
            sb.append("    in ").append(Errors.fileLocationString(displayPath, sortedLocations.getFirst()))
                    .append(System.lineSeparator());
            sb.append(highlight);
        } else {
            for (PointRange location : sortedLocations) {
                sb.append("    in ").append(Errors.fileLocationString(displayPath, location))
                        .append(System.lineSeparator());
            }
        }
    }

    private static Path relativizeToCwd(Path file) {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (file.isAbsolute() && file.startsWith(cwd)) {
            Path rel = cwd.relativize(file);
            return rel.toString().isEmpty() ? file.getFileName() : rel;
        }
        return file;
    }

    private static void appendErrorTypeHelp(
            StringBuilder sb,
            @Nullable OntClass issueType,
            Severity severity,
            Set<String> seenHelpTypes
    ) {
        if (issueType == null) {
            return;
        }
        String uri = issueType.getURI();
        if (uri == null || !seenHelpTypes.add(uri)) {
            return;
        }
        sb.append(severityLabel(severity))
                .append(" definition, hints at ")
                .append(uri)
                .append(System.lineSeparator());
    }

    private static void appendExceptionInfo(StringBuilder sb, RmlError issue, boolean verbose) {
        Exception ex = issue.exception();
        if (ex == null) {
            return;
        }
        if (verbose || RER.UnexpectedError.equals(issue.errorType())) {
            String trace = verbose ? getFullStackTrace(ex) : getTruncatedStackTrace(ex);
            sb.append("stacktrace: ").append(trace).append(System.lineSeparator());
        }
    }

    private static void appendStatistics(StringBuilder sb, RmlExecutionReport report) {
        MappingDocument plan = report.getExecutionPlan();
        int triplesMapsSize = (plan != null && plan.triplesMaps != null) ? plan.triplesMaps.size() : 0;
        long totalStatements = report.getStatistics().getGeneratedStatements();

        sb.append("Stats: ")
                .append(totalStatements)
                .append(totalStatements == 1 ? " statement from " : " statements from ")
                .append(triplesMapsSize)
                .append(triplesMapsSize == 1 ? " triples map" : " triples maps")
                .append(System.lineSeparator());

        Map<TriplesMap, Long> perMap = report.getStatistics().getGeneratedStatementPerTriplesMap();
        if (perMap != null && !perMap.isEmpty()) {
            Map<String, String> prefixMap = plan != null ? plan.prefixMap : Collections.emptyMap();
            for (var entry : perMap.entrySet()) {
                var subject = entry.getKey().subject;
                String label = subject.isURIResource()
                        ? compactIri(subject.getURI(), prefixMap)
                        : subject.toString();
                sb.append("  ")
                        .append(label)
                        .append(": ")
                        .append(entry.getValue())
                        .append(System.lineSeparator());
            }
        }
    }

    private static String compactIri(String iri, Map<String, String> prefixMap) {
        String bestPrefix = null;
        String bestNs = "";
        for (var entry : prefixMap.entrySet()) {
            String ns = entry.getValue();
            if (iri.startsWith(ns) && ns.length() > bestNs.length()) {
                bestNs = ns;
                bestPrefix = entry.getKey();
            }
        }
        if (bestPrefix != null) {
            return bestPrefix + ":" + iri.substring(bestNs.length());
        }
        return iri;
    }

    private static String getFullStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString().stripTrailing();
    }

    private static String getTruncatedStackTrace(Throwable throwable) {
        String[] lines = getFullStackTrace(throwable).split("\r?\n");
        if (lines.length <= MAX_STACK_FRAMES + 1) {
            return String.join(System.lineSeparator(), lines);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= MAX_STACK_FRAMES; i++) {
            if (i > 0) {
                sb.append(System.lineSeparator());
            }
            sb.append(lines[i]);
        }
        sb.append(System.lineSeparator())
                .append("... ")
                .append(lines.length - MAX_STACK_FRAMES - 1)
                .append(" more");
        return sb.toString();
    }
}
