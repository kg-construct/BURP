package burp;

import burp.dev.rml.RmlDevServerImpl;
import burp.dev.rml.RmlLanguageServer;
import burp.dev.rml.service.eval.AllDataContext;
import burp.dev.rml.service.selector.CursorSelector;
import burp.dev.rml.service.eval.EvaluateParams;
import burp.dev.rml.service.eval.EvaluateResult;
import burp.ls.CSVIteration;
import burp.ls.CSVReference;
import burp.model.*;
import burp.model.context.BlankNodeScope;
import burp.model.context.EvaluationContext;
import burp.parse.RmlLoader;
import burp.reporting.RmlError;
import burp.reporting.RmlExecutionReport;
import burp.vocabularies.RER;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrentEvaluationContextTest {

    @Test
    public void testConcurrentBaseIriIsolation() throws Exception {
        MappingDocument document = new RmlLoader(
                Path.of("src/test/resources/burp/eval_alice/mapping.ttl")
        ).loadMappingDocument();

        int numThreads = 30;
        List<Future<List<RdfStatement>>> futures = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(numThreads)) {
            for (int i = 0; i < numThreads; i++) {
                final String baseIri = "http://example.org/session_" + i + "/";
                futures.add(executor.submit(() -> {
                    latch.await();
                    EvaluationContext ctx = EvaluationContext.createDefault().withReport(new RmlExecutionReport()).withBaseIri(baseIri);
                    return document.generate(ctx);
                }));
            }

            latch.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }

        for (int i = 0; i < numThreads; i++) {
            List<RdfStatement> stmts = futures.get(i).get();
            assertThat(stmts).isNotEmpty();
            for (RdfStatement stmt : stmts) {
                assertThat(stmt).isNotNull();
            }
        }
    }

    @Test
    public void testConcurrentBlankNodeIsolation() throws Exception {
        int numThreads = 20;
        List<Future<Set<String>>> futures = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(numThreads)) {
            for (int i = 0; i < numThreads; i++) {
                futures.add(executor.submit(() -> {
                    latch.await();
                    BlankNodeScope scope = new BlankNodeScope();
                    Set<String> allocated = new HashSet<>();
                    for (int k = 0; k < 100; k++) {
                        allocated.add(scope.getOrAllocate("key-" + k));
                        allocated.add(scope.allocateFresh());
                    }
                    return allocated;
                }));
            }

            latch.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        for (Future<Set<String>> future : futures) {
            Set<String> set = future.get();
            assertThat(set).hasSize(200);
        }
    }

    @Test
    public void testConcurrentSubNodeEvaluation() throws Exception {
        MappingDocument document = new RmlLoader(
                Path.of("src/test/resources/burp/eval_alice/mapping.ttl")
        ).loadMappingDocument();
        List<TriplesMap> triplesMaps = document.triplesMaps;

        TriplesMap tm = triplesMaps.getFirst();
        SubjectMap sm = tm.subjectMap;
        PredicateObjectMap pom = tm.predicateObjectMaps.getFirst();
        BaseObjectMap om = pom.objectMaps.getFirst();

        Iteration testIter = tm.logicalSource.iterator().iterator().next();

        int numThreads = 30;
        List<Future<List<RmlEvaluationResult>>> futures = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(numThreads)) {
            for (int i = 0; i < numThreads; i++) {
                final int nodeType = i % 4;
                futures.add(executor.submit(() -> {
                    latch.await();
                    EvaluationContext ctx = EvaluationContext.createDefault().withReport(new RmlExecutionReport()).withBaseIri("http://example.org/base/");
                    return switch (nodeType) {
                        case 0 -> sm.evaluateNode(testIter, ctx);
                        case 1 -> pom.evaluateNode(testIter, ctx);
                        case 2 -> om.evaluateNode(testIter, ctx);
                        case 3 -> tm.evaluateNode(testIter, ctx);
                        default -> Collections.emptyList();
                    };
                }));
            }

            latch.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        for (int i = 0; i < numThreads; i++) {
            List<RmlEvaluationResult> results = futures.get(i).get();
            assertThat(results).isNotEmpty();
        }
    }

    @Test
    public void testConcurrentTemplateSafetyIsolation() throws Exception {
        Template template = new Template("http://example.org/{name}/{tag}");
        for (Template.Segment segment : template.getSegments()) {
            if (segment instanceof Template.ReferenceSegment rs && rs.reference() instanceof RawReference rr) {
                rr.setCompiledReference(new CSVReference(rr.getReference(), null));
            }
        }

        Iteration iter = new CSVIteration(
                new String[]{"name", "tag"},
                new String[]{"John Doe", "A&B C"},
                Collections.emptySet()
        );

        int numThreads = 20;
        List<Future<List<String>>> futures = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(numThreads)) {
            for (int i = 0; i < numThreads; i++) {
                final int mode = i % 2;
                futures.add(executor.submit(() -> {
                    latch.await();
                    EvaluationContext baseCtx = EvaluationContext.createDefault();
                    TemplateReferenceSafety safety = (mode == 0) ? TemplateReferenceSafety.SAFE_IRI : TemplateReferenceSafety.UNSAFE;
                    EvaluationContext tplCtx = baseCtx.withTemplateSafety(safety);
                    assertThat(tplCtx.templateSafety()).isEqualTo(safety);
                    return template.values(iter, tplCtx);
                }));
            }

            latch.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        for (int i = 0; i < numThreads; i++) {
            List<String> values = futures.get(i).get();
            assertThat(values).hasSize(1);
            if (i % 2 == 0) {
                // SAFE_IRI encodes space and reserved characters
                assertThat(values.getFirst()).isEqualTo("http://example.org/John%20Doe/A%26B%20C");
            } else {
                // UNSAFE keeps raw
                assertThat(values.getFirst()).isEqualTo("http://example.org/John Doe/A&B C");
            }
        }
    }

    @Test
    public void testConcurrentErrorReportIsolation() throws Exception {
        int numThreads = 20;
        List<Future<Integer>> futures = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(numThreads)) {
            for (int i = 0; i < numThreads; i++) {
                final int errorCount = i + 1;
                futures.add(executor.submit(() -> {
                    latch.await();
                    EvaluationContext ctx = EvaluationContext.createDefault();
                    for (int k = 0; k < errorCount; k++) {
                        ctx.report().getErrors().add(new RmlError("Error " + k, null, RER.ExecutionError));
                    }
                    return ctx.report().getErrors().size();
                }));
            }

            latch.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        for (int i = 0; i < numThreads; i++) {
            int count = futures.get(i).get();
            assertThat(count).isEqualTo(i + 1);
        }
    }

    @Test
    public void testDevServerConcurrentEvaluationRequests() throws Exception {
        RmlLanguageServer server = new RmlDevServerImpl();

        int numThreads = 15;
        List<Future<List<EvaluateResult>>> futures = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(numThreads)) {
            for (int i = 0; i < numThreads; i++) {
                futures.add(executor.submit(() -> {
                    latch.await();
                    EvaluateParams params = new EvaluateParams(
                            new CursorSelector("src/test/resources/burp/eval_alice/mapping.ttl", new Position(6, 1)),
                            new AllDataContext()
                    );
                    return server.evaluate(params).get();
                }));
            }

            latch.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }

        for (Future<List<EvaluateResult>> future : futures) {
            List<EvaluateResult> results = future.get();
            assertThat(results).isNotNull().hasSize(3);
            assertThat(results.get(0).iterationId()).isEqualTo("0");
            assertThat(results.get(1).iterationId()).isEqualTo("1");
            assertThat(results.get(2).iterationId()).isEqualTo("2");
        }
    }
}
