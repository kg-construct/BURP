package burp.reporting;

import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/// Thread-safe, bounded aggregator for {@link RmlError}s keyed by {@code (errorTypeUri, mappingOriginKey)}.
///
/// Prevents per-iteration error flooding on large datasets by retaining at most {@code maxSamples}
/// {@link SampleOccurrence} entries per key and counting remaining occurrences in {@code additionalCount}.
@NullMarked
public final class RmlErrorAggregator {

    public static final int DEFAULT_MAX_SAMPLES = 3;

    private record AggregationKey(String errorTypeUri, String mappingOriginKey) {
    }

    private static final class MutableAggregate {
        final RmlError primary;
        final List<SampleOccurrence> samples = new ArrayList<>();
        int additionalCount = 0;

        MutableAggregate(RmlError primary) {
            this.primary = primary;
        }

        AggregatedRmlError snapshot() {
            return new AggregatedRmlError(primary, List.copyOf(samples), additionalCount);
        }
    }

    private final int maxSamples;
    private final Map<AggregationKey, MutableAggregate> entries = new LinkedHashMap<>();

    public RmlErrorAggregator() {
        this(DEFAULT_MAX_SAMPLES);
    }

    public RmlErrorAggregator(int maxSamples) {
        if (maxSamples < 0) {
            throw new IllegalArgumentException("maxSamples must be >= 0, got " + maxSamples);
        }
        this.maxSamples = maxSamples;
    }

    public synchronized void add(RmlError error) {
        String errorTypeUri = error.errorType().getURI();
        if (errorTypeUri == null) {
            errorTypeUri = error.errorType().toString();
        }
        AggregationKey key = new AggregationKey(errorTypeUri, mappingOriginKey(error));
        MutableAggregate existing = entries.get(key);
        if (existing == null) {
            MutableAggregate created = new MutableAggregate(error);
            if (maxSamples > 0) {
                created.samples.add(new SampleOccurrence(error.originLineage(), error.evaluationResult()));
            }
            entries.put(key, created);
            return;
        }
        existing.additionalCount++;
        if (existing.samples.size() < maxSamples) {
            existing.samples.add(new SampleOccurrence(error.originLineage(), error.evaluationResult()));
        }
    }

    public synchronized List<AggregatedRmlError> getAggregatedLogs() {
        return entries.values().stream()
                .map(MutableAggregate::snapshot)
                .toList();
    }

    public synchronized List<RmlError> getErrors() {
        return getBySeverity(Severity.ERROR);
    }

    public synchronized List<RmlError> getWarnings() {
        return getBySeverity(Severity.WARNING);
    }

    public synchronized List<RmlError> getBySeverity(Severity severity) {
        return entries.values().stream()
                .map(e -> e.primary)
                .filter(e -> e.severity() == severity)
                .toList();
    }

    public synchronized boolean isEmpty() {
        return entries.isEmpty();
    }

    public synchronized void clear() {
        entries.clear();
    }

    private static String mappingOriginKey(RmlError error) {
        Origin origin = error.origin();
        if (origin != null) {
            List<PointRange> ranges = origin.ranges();
            if (!ranges.isEmpty()) {
                Path file = origin.file();
                String filePrefix = file != null ? file.normalize() + ":" : "";
                return ranges.stream()
                        .map(r -> filePrefix + r.start().line() + ":" + r.start().column()
                                + "-" + r.end().line() + ":" + r.end().column())
                        .sorted()
                        .collect(Collectors.joining("|"));
            }
            List<? extends RDFPointer> pointers = origin.sourceStatements();
            if (pointers != null && !pointers.isEmpty()) {
                return pointers.stream()
                        .map(RmlErrorAggregator::canonicalPointerKey)
                        .sorted()
                        .collect(Collectors.joining("|"));
            }
        }
        String planNodeId = error.planNode() != null
                ? String.valueOf(System.identityHashCode(error.planNode()))
                : "";
        return "unlocated:" + planNodeId + ":" + error.message();
    }

    private static String canonicalPointerKey(RDFPointer pointer) {
        return switch (pointer) {
            case TextFilePointer(Path path, PointRange range) ->
                    "tfp:" + (path != null ? path.normalize() : "") + ":"
                            + range.start().line() + ":" + range.start().column()
                            + "-" + range.end().line() + ":" + range.end().column();
            case LiteralPart(Statement stmt, PointRange range) ->
                    "lit:" + canonicalStatementKey(stmt) + ":"
                            + range.start().line() + ":" + range.start().column();
            case StatementParts(Statement stmt, boolean subj, boolean pred, boolean obj) ->
                    "sp:" + canonicalStatementKey(stmt) + ":" + subj + ":" + pred + ":" + obj;
            default -> pointer.toString();
        };
    }

    private static String canonicalStatementKey(Statement stmt) {
        String s = canonicalNodeKey(stmt.getSubject());
        String p = stmt.getPredicate().getURI();
        String o = canonicalNodeKey(stmt.getObject());
        return s + " " + p + " " + o;
    }

    private static String canonicalNodeKey(RDFNode node) {
        if (node.isAnon()) {
            return "_:anon";
        }
        if (node.isURIResource()) {
            return "<" + node.asResource().getURI() + ">";
        }
        return node.toString();
    }
}
