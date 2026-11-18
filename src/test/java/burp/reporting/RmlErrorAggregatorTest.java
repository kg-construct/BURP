package burp.reporting;

import burp.model.IterationId;
import burp.model.OriginLineage;
import burp.model.RmlEvaluationResult;
import burp.model.rdf.LiteralTerm;
import burp.parse.ValidationMode;
import burp.parse.turtleprov.Point;
import burp.parse.turtleprov.Point;
import burp.vocabularies.RER;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static burp.vocabularies.PTR.Point;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RmlErrorAggregatorTest {

    private static Origin textOrigin(int startLine) {
        return new Origin(List.of(new TextFilePointer(
                Path.of("mapping.ttl"),
                new PointRange(new Point(startLine, 1), new Point(startLine, 20))
        )));
    }

    private static RmlEvaluationResult sampleEval(String val) {
        return new LiteralTerm(val, null, null);
    }

    @Test
    void capsSamplesAtThreeAndCountsAdditionalOccurrences() {
        RmlErrorAggregator aggregator = new RmlErrorAggregator();
        Origin origin = textOrigin(10);

        for (int i = 0; i < 6; i++) {
            aggregator.add(new RmlError(
                    "Invalid value " + i,
                    origin,
                    RER.OntologyDatatypeMismatch,
                    null,
                    null,
                    null,
                    OriginLineage.of(new IterationId("ls", i)),
                    sampleEval("bad-" + i)
            ));
        }

        List<AggregatedRmlError> logs = aggregator.getAggregatedLogs();
        assertThat(logs).hasSize(1);
        AggregatedRmlError agg = logs.getFirst();
        assertThat(agg.primary().message()).isEqualTo("Invalid value 0");
        assertThat(agg.samples()).hasSize(3);
        assertThat(agg.samples())
                .extracting(s -> s.originLineage().primary().index())
                .containsExactly(0, 1, 2);
        assertThat(agg.additionalCount()).isEqualTo(5);
    }

    @Test
    void separatesDistinctOriginsAndUnlocatedMessages() {
        RmlErrorAggregator aggregator = new RmlErrorAggregator();
        aggregator.add(new RmlError("Err A", textOrigin(10), RER.InvalidIRI));
        aggregator.add(new RmlError("Err B", textOrigin(20), RER.InvalidIRI));
        aggregator.add(new RmlError("Unlocated 1", null, RER.MappingError));
        aggregator.add(new RmlError("Unlocated 2", null, RER.MappingError));

        assertThat(aggregator.getAggregatedLogs()).hasSize(4);
    }

    @Test
    void canonicalizesBlankNodeStatementParts() {
        Model m1 = ModelFactory.createDefaultModel();
        Model m2 = ModelFactory.createDefaultModel();
        Statement s1 = m1.createStatement(m1.createResource(), ResourceFactory.createProperty("http://w3id.org/rml/template"), "a");
        Statement s2 = m2.createStatement(m2.createResource(), ResourceFactory.createProperty("http://w3id.org/rml/template"), "a");

        RmlErrorAggregator aggregator = new RmlErrorAggregator();
        aggregator.add(new RmlError("Same blank-node stmt", new Origin(s1, StatementPart.Object), RER.TemplateSyntaxError));
        aggregator.add(new RmlError("Same blank-node stmt", new Origin(s2, StatementPart.Object), RER.TemplateSyntaxError));

        assertThat(aggregator.getAggregatedLogs()).hasSize(1);
        assertThat(aggregator.getAggregatedLogs().getFirst().additionalCount()).isEqualTo(1);
    }

    @Test
    void strictEmitThrowsOnlyForErrorsAndKeepsWarningsNonFatal() {
        RmlExecutionReport report = new RmlExecutionReport();
        RmlError warning = new RmlError("Datatype warning", textOrigin(5), RER.OntologyDatatypeMismatch);
        RmlError info = new RmlError("Info note", textOrigin(6), RER.Information);
        RmlError error = new RmlError("Fatal error", textOrigin(7), RER.InvalidIRI);

        report.emit(warning, ValidationMode.STRICT);
        report.emit(info, ValidationMode.STRICT);

        assertThat(report.getErrors()).isEmpty();
        assertThat(report.getWarnings()).containsExactly(warning);
        assertThat(report.getAggregatedLogs()).hasSize(2);

        assertThatThrownBy(() -> report.emit(error, ValidationMode.STRICT))
                .isInstanceOf(BurpException.class);
        assertThat(report.getErrors()).containsExactly(error);
    }

    @Test
    void concurrentAddsProduceConsistentSnapshots() throws Exception {
        RmlErrorAggregator aggregator = new RmlErrorAggregator(3);
        int threads = 8;
        int perThread = 50;
        CountDownLatch start = new CountDownLatch(1);
        Origin origin = textOrigin(42);

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                final int tid = t;
                pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        aggregator.add(new RmlError(
                                "Concurrent warning",
                                origin,
                                RER.OntologyDatatypeMismatch,
                                null,
                                null,
                                null,
                                OriginLineage.of(new IterationId("ls", tid * perThread + i)),
                                sampleEval("v")
                        ));
                    }
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        List<AggregatedRmlError> logs = aggregator.getAggregatedLogs();
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().samples()).hasSize(3);
        assertThat(logs.getFirst().additionalCount()).isEqualTo(threads * perThread - 1);
    }
}
