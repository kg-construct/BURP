package perf.incremental;

import burp.dev.rml.service.eval.AllDataContext;
import burp.dev.rml.service.eval.EvaluateParams;
import burp.dev.rml.service.eval.EvaluateResult;
import burp.dev.rml.service.selector.IdSelector;
import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.eval.RmlEvaluator;
import burp.dev.rml.service.cache.DevServerCache;
import burp.model.PlanNode;
import burp.model.PlanNodeIdRegistry;
import burp.model.RdfStatement;
import burp.model.RmlEvaluationResult;
import burp.model.TriplesMap;
import burp.model.rdf.BlankNodeTerm;
import burp.model.rdf.IRITerm;
import burp.model.rdf.LiteralTerm;
import io.carml.engine.rdf.RdfRmlMapper;
import io.carml.model.Mapping;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.eclipse.rdf4j.model.util.ModelCollector;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class IncrementalPerformanceTest {

    private static int getRecordsCount() {
        return Integer.parseInt(System.getProperty("records", "50000"));
    }

    private static int getWarmupRuns() {
        return Integer.parseInt(System.getProperty("warmup", "2"));
    }

    private static int getMeasuredRuns() {
        return Integer.parseInt(System.getProperty("runs", "5"));
    }

    public record RunCacheEvolution(com.google.common.cache.CacheStats coldStats,
                                    com.google.common.cache.CacheStats mutateStats,
                                    com.google.common.cache.CacheStats revertStats, long sourceCacheSize,
                                    long joinCacheSize, long memoCacheSize) {
    }

    public record BenchmarkStats(String engine, double avgRun1Ms, double avgRun2Ms, double avgRun3Ms,
                                 double speedupMutate, double speedupRevert, double avgMemMb, long hitsDeltaRun2,
                                 long hitsDeltaRun3, boolean allSparqlPassed, RunCacheEvolution cacheEvolution) {
    }

    // ---------------------------------------------------------------------------------------------
    // BURP Runner (No Cache / Direct Baseline)
    // ---------------------------------------------------------------------------------------------
    private BenchmarkStats runBurpNoCacheScenario(String baseMappingPath, String modifiedMappingPath, String query1Path, String query2Path) throws Exception {
        MappingWorkspace workspace = new MappingWorkspace();
        RmlEvaluator evaluator = new RmlEvaluator(null); // Direct evaluation without cache
        String baseContent = Files.readString(Path.of(baseMappingPath));
        String modifiedContent = Files.readString(Path.of(modifiedMappingPath));

        // --- WARMUP ---
        for (int i = 0; i < getWarmupRuns(); i++) {
            workspace.updateDocumentText(baseMappingPath, baseContent);
            var doc = workspace.loadMappingWithStableIds(baseMappingPath);
            evaluator.evaluate(new EvaluateParams(new IdSelector(baseMappingPath, PlanNodeIdRegistry.getId(doc.triplesMaps.getFirst())), new AllDataContext()), workspace).get();
            workspace.updateDocumentText(baseMappingPath, modifiedContent);
            var docMod = workspace.loadMappingWithStableIds(baseMappingPath);
            evaluator.evaluate(new EvaluateParams(new IdSelector(baseMappingPath, PlanNodeIdRegistry.getId(docMod.triplesMaps.getFirst())), new AllDataContext()), workspace).get();
            workspace.updateDocumentText(baseMappingPath, baseContent);
            var docRev = workspace.loadMappingWithStableIds(baseMappingPath);
            evaluator.evaluate(new EvaluateParams(new IdSelector(baseMappingPath, PlanNodeIdRegistry.getId(docRev.triplesMaps.getFirst())), new AllDataContext()), workspace).get();
        }

        List<Double> run1Times = new ArrayList<>();
        List<Double> run2Times = new ArrayList<>();
        List<Double> run3Times = new ArrayList<>();
        List<Double> memSamples = new ArrayList<>();
        boolean sparqlPassedAll = true;

        for (int run = 0; run < getMeasuredRuns(); run++) {
            workspace.updateDocumentText(baseMappingPath, baseContent);

            // Run 1 (Cold Base Mapping)
            long memBefore = getUsedMemory();
            List<TriplesMap> tms1 = workspace.loadMappingWithStableIds(baseMappingPath).triplesMaps;
            PlanNode target1 = tms1.getFirst();
            String id1 = PlanNodeIdRegistry.getId(target1);
            EvaluateParams params1 = new EvaluateParams(new IdSelector(baseMappingPath, id1), new AllDataContext());

            long t1Start = System.nanoTime();
            List<EvaluateResult> r1 = evaluator.evaluate(params1, workspace).get();
            long t1Duration = System.nanoTime() - t1Start;
            long memAfter = getUsedMemory();

            Model m1 = convertBurpToJenaModel(r1);
            sparqlPassedAll &= verifyWithSparql(m1, query1Path);

            // Run 2 (Mutated Mapping)
            workspace.updateDocumentText(baseMappingPath, modifiedContent);
            List<TriplesMap> tms2 = workspace.loadMappingWithStableIds(baseMappingPath).triplesMaps;
            PlanNode target2 = tms2.getFirst();
            String id2 = PlanNodeIdRegistry.getId(target2);
            EvaluateParams params2 = new EvaluateParams(new IdSelector(baseMappingPath, id2), new AllDataContext());

            long t2Start = System.nanoTime();
            List<EvaluateResult> r2 = evaluator.evaluate(params2, workspace).get();
            long t2Duration = System.nanoTime() - t2Start;

            Model m2 = convertBurpToJenaModel(r2);
            sparqlPassedAll &= verifyWithSparql(m2, query2Path);

            // Run 3 (Revert to Base Mapping)
            workspace.updateDocumentText(baseMappingPath, baseContent);
            List<TriplesMap> tms3 = workspace.loadMappingWithStableIds(baseMappingPath).triplesMaps;
            PlanNode target3 = tms3.getFirst();
            String id3 = PlanNodeIdRegistry.getId(target3);
            EvaluateParams params3 = new EvaluateParams(new IdSelector(baseMappingPath, id3), new AllDataContext());

            long t3Start = System.nanoTime();
            List<EvaluateResult> r3 = evaluator.evaluate(params3, workspace).get();
            long t3Duration = System.nanoTime() - t3Start;

            Model m3 = convertBurpToJenaModel(r3);
            sparqlPassedAll &= verifyWithSparql(m3, query1Path);

            run1Times.add(t1Duration / 1_000_000.0);
            run2Times.add(t2Duration / 1_000_000.0);
            run3Times.add(t3Duration / 1_000_000.0);
            memSamples.add(Math.max(0, memAfter - memBefore) / (1024.0 * 1024.0));
        }

        double avgRun1 = run1Times.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgRun2 = run2Times.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgRun3 = run3Times.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double speedupMutate = avgRun1 / Math.max(0.0001, avgRun2);
        double speedupRevert = avgRun1 / Math.max(0.0001, avgRun3);
        double avgMem = memSamples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        return new BenchmarkStats("BURP (No Cache)", avgRun1, avgRun2, avgRun3, speedupMutate, speedupRevert, avgMem, 0, 0, sparqlPassedAll, null);
    }

    // ---------------------------------------------------------------------------------------------
    // BURP Runner (Cold vs Warm Incremental vs Warm Revert AST Cache)
    // ---------------------------------------------------------------------------------------------
    private BenchmarkStats runBurpScenario(String baseMappingPath, String modifiedMappingPath, String query1Path, String query2Path) throws Exception {
        MappingWorkspace workspace = new MappingWorkspace();
        DevServerCache cache = workspace.getCache();
        RmlEvaluator evaluator = new RmlEvaluator(cache);
        String baseContent = Files.readString(Path.of(baseMappingPath));
        String modifiedContent = Files.readString(Path.of(modifiedMappingPath));

        // --- WARMUP ---
        for (int i = 0; i < getWarmupRuns(); i++) {
            workspace.updateDocumentText(baseMappingPath, baseContent);
            List<TriplesMap> tms = workspace.loadMappingWithStableIds(baseMappingPath).triplesMaps;
            evaluator.evaluate(new EvaluateParams(new IdSelector(baseMappingPath, PlanNodeIdRegistry.getId(tms.getFirst())), new AllDataContext()), workspace).get();
            workspace.updateDocumentText(baseMappingPath, modifiedContent);
            List<TriplesMap> tmsMod = workspace.loadMappingWithStableIds(baseMappingPath).triplesMaps;
            evaluator.evaluate(new EvaluateParams(new IdSelector(baseMappingPath, PlanNodeIdRegistry.getId(tmsMod.getFirst())), new AllDataContext()), workspace).get();
            workspace.updateDocumentText(baseMappingPath, baseContent);
            List<TriplesMap> tmsRev = workspace.loadMappingWithStableIds(baseMappingPath).triplesMaps;
            evaluator.evaluate(new EvaluateParams(new IdSelector(baseMappingPath, PlanNodeIdRegistry.getId(tmsRev.getFirst())), new AllDataContext()), workspace).get();
        }

        List<Double> run1Times = new ArrayList<>();
        List<Double> run2Times = new ArrayList<>();
        List<Double> run3Times = new ArrayList<>();
        List<Double> memSamples = new ArrayList<>();
        long totalHitsDeltaRun2 = 0;
        long totalHitsDeltaRun3 = 0;
        boolean sparqlPassedAll = true;

        com.google.common.cache.CacheStats lastColdStats = null;
        com.google.common.cache.CacheStats lastMutateStats = null;
        com.google.common.cache.CacheStats lastRevertStats = null;

        for (int run = 0; run < getMeasuredRuns(); run++) {
            cache.invalidateAll();
            workspace.updateDocumentText(baseMappingPath, baseContent);

            // Cold Run 1 (Base Mapping)
            long memBefore = getUsedMemory();
            List<TriplesMap> tms1 = workspace.loadMappingWithStableIds(baseMappingPath).triplesMaps;
            PlanNode target1 = tms1.getFirst();
            String id1 = PlanNodeIdRegistry.getId(target1);
            EvaluateParams params1 = new EvaluateParams(new IdSelector(baseMappingPath, id1), new AllDataContext());

            long hitsBefore1 = cache.getHitCount();
            long t1Start = System.nanoTime();
            List<EvaluateResult> r1 = evaluator.evaluate(params1, workspace).get();
            long t1Duration = System.nanoTime() - t1Start;
            long memAfter = getUsedMemory();
            lastColdStats = cache.getStats();

            Model m1 = convertBurpToJenaModel(r1);
            sparqlPassedAll &= verifyWithSparql(m1, query1Path);

            // Warm Incremental Run 2 (Mutated Mapping)
            workspace.updateDocumentText(baseMappingPath, modifiedContent);
            List<TriplesMap> tms2 = workspace.loadMappingWithStableIds(baseMappingPath).triplesMaps;
            PlanNode target2 = tms2.getFirst();
            String id2 = PlanNodeIdRegistry.getId(target2);
            EvaluateParams params2 = new EvaluateParams(new IdSelector(baseMappingPath, id2), new AllDataContext());

            long hitsBefore2 = cache.getHitCount();
            long t2Start = System.nanoTime();
            List<EvaluateResult> r2 = evaluator.evaluate(params2, workspace).get();
            long t2Duration = System.nanoTime() - t2Start;
            long hitsAfter2 = cache.getHitCount();
            lastMutateStats = cache.getStats();

            Model m2 = convertBurpToJenaModel(r2);
            sparqlPassedAll &= verifyWithSparql(m2, query2Path);
            totalHitsDeltaRun2 += (hitsAfter2 - hitsBefore2);

            // Warm Revert Run 3 (Revert back to Base Mapping)
            workspace.updateDocumentText(baseMappingPath, baseContent);
            List<TriplesMap> tms3 = workspace.loadMappingWithStableIds(baseMappingPath).triplesMaps;
            PlanNode target3 = tms3.getFirst();
            String id3 = PlanNodeIdRegistry.getId(target3);
            EvaluateParams params3 = new EvaluateParams(new IdSelector(baseMappingPath, id3), new AllDataContext());

            long hitsBefore3 = cache.getHitCount();
            long t3Start = System.nanoTime();
            List<EvaluateResult> r3 = evaluator.evaluate(params3, workspace).get();
            long t3Duration = System.nanoTime() - t3Start;
            long hitsAfter3 = cache.getHitCount();
            lastRevertStats = cache.getStats();

            Model m3 = convertBurpToJenaModel(r3);
            sparqlPassedAll &= verifyWithSparql(m3, query1Path);
            totalHitsDeltaRun3 += (hitsAfter3 - hitsBefore3);

            run1Times.add(t1Duration / 1_000_000.0);
            run2Times.add(t2Duration / 1_000_000.0);
            run3Times.add(t3Duration / 1_000_000.0);
            memSamples.add(Math.max(0, memAfter - memBefore) / (1024.0 * 1024.0));
        }

        double avgRun1 = run1Times.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgRun2 = run2Times.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgRun3 = run3Times.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double speedupMutate = avgRun1 / Math.max(0.0001, avgRun2);
        double speedupRevert = avgRun1 / Math.max(0.0001, avgRun3);
        double avgMem = memSamples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        RunCacheEvolution evo = new RunCacheEvolution(lastColdStats, lastMutateStats, lastRevertStats, cache.getSourceIterationCacheSize(), cache.getParentJoinCacheSize(), cache.getNodeMemoCacheSize());

        return new BenchmarkStats("BURP (Incremental)", avgRun1, avgRun2, avgRun3, speedupMutate, speedupRevert, avgMem, totalHitsDeltaRun2 / getMeasuredRuns(), totalHitsDeltaRun3 / getMeasuredRuns(), sparqlPassedAll, evo);
    }

    // ---------------------------------------------------------------------------------------------
    // CARML Runner (Batch Execution)
    // ---------------------------------------------------------------------------------------------
    private BenchmarkStats runCarmlScenario(String baseMappingPath, String modifiedMappingPath, String query1Path, String query2Path, Path dataDir) throws Exception {
        // --- WARMUP ---
        for (int i = 0; i < getWarmupRuns(); i++) {
            try (InputStream is1 = Files.newInputStream(Path.of(baseMappingPath)); InputStream is2 = Files.newInputStream(Path.of(modifiedMappingPath)); InputStream is3 = Files.newInputStream(Path.of(baseMappingPath))) {
                Mapping m1 = Mapping.of(RDFFormat.TURTLE, is1);
                RdfRmlMapper mapper1 = RdfRmlMapper.builder().fileResolver(dataDir).mapping(m1).build();
                mapper1.map().collect(ModelCollector.toTreeModel()).block();

                Mapping m2 = Mapping.of(RDFFormat.TURTLE, is2);
                RdfRmlMapper mapper2 = RdfRmlMapper.builder().fileResolver(dataDir).mapping(m2).build();
                mapper2.map().collect(ModelCollector.toTreeModel()).block();

                Mapping m3 = Mapping.of(RDFFormat.TURTLE, is3);
                RdfRmlMapper mapper3 = RdfRmlMapper.builder().fileResolver(dataDir).mapping(m3).build();
                mapper3.map().collect(ModelCollector.toTreeModel()).block();
            }
        }

        List<Double> run1Times = new ArrayList<>();
        List<Double> run2Times = new ArrayList<>();
        List<Double> run3Times = new ArrayList<>();
        List<Double> memSamples = new ArrayList<>();
        boolean sparqlPassedAll = true;

        for (int run = 0; run < getMeasuredRuns(); run++) {
            // Run 1 (Cold Base Mapping)
            long memBefore = getUsedMemory();
            long t1Start = System.nanoTime();
            org.eclipse.rdf4j.model.Model r1;
            try (InputStream is1 = Files.newInputStream(Path.of(baseMappingPath))) {
                Mapping m1 = Mapping.of(RDFFormat.TURTLE, is1);
                RdfRmlMapper mapper1 = RdfRmlMapper.builder().fileResolver(dataDir).mapping(m1).build();
                r1 = mapper1.map().collect(ModelCollector.toTreeModel()).block();
            }
            long t1Duration = System.nanoTime() - t1Start;
            long memAfter = getUsedMemory();

            Model jenaM1 = convertRdf4jToJenaModel(r1);
            sparqlPassedAll &= verifyWithSparql(jenaM1, query1Path);

            // Run 2 (Mutated Mapping)
            long t2Start = System.nanoTime();
            org.eclipse.rdf4j.model.Model r2;
            try (InputStream is2 = Files.newInputStream(Path.of(modifiedMappingPath))) {
                Mapping m2 = Mapping.of(RDFFormat.TURTLE, is2);
                RdfRmlMapper mapper2 = RdfRmlMapper.builder().fileResolver(dataDir).mapping(m2).build();
                r2 = mapper2.map().collect(ModelCollector.toTreeModel()).block();
            }
            long t2Duration = System.nanoTime() - t2Start;

            Model jenaM2 = convertRdf4jToJenaModel(r2);
            sparqlPassedAll &= verifyWithSparql(jenaM2, query2Path);

            // Run 3 (Revert to Base Mapping)
            long t3Start = System.nanoTime();
            org.eclipse.rdf4j.model.Model r3;
            try (InputStream is3 = Files.newInputStream(Path.of(baseMappingPath))) {
                Mapping m3 = Mapping.of(RDFFormat.TURTLE, is3);
                RdfRmlMapper mapper3 = RdfRmlMapper.builder().fileResolver(dataDir).mapping(m3).build();
                r3 = mapper3.map().collect(ModelCollector.toTreeModel()).block();
            }
            long t3Duration = System.nanoTime() - t3Start;

            Model jenaM3 = convertRdf4jToJenaModel(r3);
            sparqlPassedAll &= verifyWithSparql(jenaM3, query1Path);

            run1Times.add(t1Duration / 1_000_000.0);
            run2Times.add(t2Duration / 1_000_000.0);
            run3Times.add(t3Duration / 1_000_000.0);
            memSamples.add(Math.max(0, memAfter - memBefore) / (1024.0 * 1024.0));
        }

        double avgRun1 = run1Times.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgRun2 = run2Times.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgRun3 = run3Times.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double speedupMutate = avgRun1 / Math.max(0.0001, avgRun2);
        double speedupRevert = avgRun1 / Math.max(0.0001, avgRun3);
        double avgMem = memSamples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        return new BenchmarkStats("CARML (Batch)", avgRun1, avgRun2, avgRun3, speedupMutate, speedupRevert, avgMem, 0, 0, sparqlPassedAll, null);
    }

    private void printComparison(String scenarioName, int recordCount, BenchmarkStats burpNoCache, BenchmarkStats burpIncr, BenchmarkStats carml) {
        int warmup = getWarmupRuns();
        int measured = getMeasuredRuns();
        System.out.println("\n==========================================================================================================================");
        System.out.printf(" SCENARIO: %s (Records: %,d | %d measured runs after %d warmups)%n", scenarioName, recordCount, measured, warmup);
        System.out.println("==========================================================================================================================");
        System.out.printf("%-20s | %-12s | %-12s | %-12s | %-10s | %-10s | %-10s | %-10s%n", "Engine", "Run 1 (Cold)", "Run 2 (Mutate)", "Run 3 (Revert)", "Mut Speed", "Rev Speed", "Mem Delta", "SPARQL Ok");
        System.out.println("---------------------+--------------+--------------+--------------+------------+------------+------------+-----------");
        System.out.printf("%-20s | %8.3f ms  | %8.3f ms  | %8.3f ms  | %8.2fx   | %8.2fx   | %8.2f MB | %-10s%n", burpNoCache.engine(), burpNoCache.avgRun1Ms(), burpNoCache.avgRun2Ms(), burpNoCache.avgRun3Ms(), burpNoCache.speedupMutate(), burpNoCache.speedupRevert(), burpNoCache.avgMemMb(), burpNoCache.allSparqlPassed());
        System.out.printf("%-20s | %8.3f ms  | %8.3f ms  | %8.3f ms  | %8.2fx   | %8.2fx   | %8.2f MB | %-10s%n", burpIncr.engine(), burpIncr.avgRun1Ms(), burpIncr.avgRun2Ms(), burpIncr.avgRun3Ms(), burpIncr.speedupMutate(), burpIncr.speedupRevert(), burpIncr.avgMemMb(), burpIncr.allSparqlPassed());
        System.out.printf("%-20s | %8.3f ms  | %8.3f ms  | %8.3f ms  | %8.2fx   | %8.2fx   | %8.2f MB | %-10s%n", carml.engine(), carml.avgRun1Ms(), carml.avgRun2Ms(), carml.avgRun3Ms(), carml.speedupMutate(), carml.speedupRevert(), carml.avgMemMb(), carml.allSparqlPassed());
        System.out.println("--------------------------------------------------------------------------------------------------------------------------");
        double mutateVsCarml = carml.avgRun2Ms() / Math.max(0.0001, burpIncr.avgRun2Ms());
        double revertVsCarml = carml.avgRun3Ms() / Math.max(0.0001, burpIncr.avgRun3Ms());
        double mutateVsNoCache = burpNoCache.avgRun2Ms() / Math.max(0.0001, burpIncr.avgRun2Ms());
        double revertVsNoCache = burpNoCache.avgRun3Ms() / Math.max(0.0001, burpIncr.avgRun3Ms());
        System.out.printf("=> BURP Incremental (Run 2) is %.2fx faster vs CARML and %.2fx vs BURP Direct (No-Cache)%n", mutateVsCarml, mutateVsNoCache);
        System.out.printf("=> BURP Reverted    (Run 3) is %.2fx faster vs CARML and %.2fx vs BURP Direct (No-Cache)%n", revertVsCarml, revertVsNoCache);

        if (burpIncr.cacheEvolution() != null) {
            var evo = burpIncr.cacheEvolution();
            System.out.println("--------------------------------------------------------------------------------------------------------------------------");
            System.out.println(" [CACHE EVOLUTION ACROSS RUNS]");
            System.out.printf("   * After Run 1 (Cold Start) : Requests: %,d (Hits: %,d | Misses: %,d | Hit Rate: %.2f%%)%n", evo.coldStats().requestCount(), evo.coldStats().hitCount(), evo.coldStats().missCount(), evo.coldStats().hitRate() * 100.0);
            System.out.printf("   * After Run 2 (Mutate)     : Requests: %,d (Hits: %,d | Misses: %,d | Hit Rate: %.2f%%)%n", evo.mutateStats().requestCount(), evo.mutateStats().hitCount(), evo.mutateStats().missCount(), evo.mutateStats().hitRate() * 100.0);
            System.out.printf("   * After Run 3 (Revert/Undo): Requests: %,d (Hits: %,d | Misses: %,d | Hit Rate: %.2f%%)%n", evo.revertStats().requestCount(), evo.revertStats().hitCount(), evo.revertStats().missCount(), evo.revertStats().hitRate() * 100.0);
            System.out.printf("   * Current Tier Sizes       : Sources: %,d | Join Tables: %,d | Memoized Nodes: %,d entries%n", evo.sourceCacheSize(), evo.joinCacheSize(), evo.memoCacheSize());
        }
        System.out.println("==========================================================================================================================\n");
    }

    @Test
    public void testParentSubjectChangeIncremental() throws Exception {
        int records = getRecordsCount();
        Path dataDir = Path.of("src/test/resources/perf/incremental/parent_subject_change");
        IncrementalDataGenerator.generateParentSubjectChangeData(dataDir, records);

        String baseMapping = dataDir.resolve("mapping_base.ttl").toString();
        String modifiedMapping = dataDir.resolve("mapping_modified.ttl").toString();
        String q1 = dataDir.resolve("verify_run1.rq").toString();
        String q2 = dataDir.resolve("verify_run2.rq").toString();

        BenchmarkStats burpNoCache = runBurpNoCacheScenario(baseMapping, modifiedMapping, q1, q2);
        BenchmarkStats burpStats = runBurpScenario(baseMapping, modifiedMapping, q1, q2);
        BenchmarkStats carmlStats = runCarmlScenario(baseMapping, modifiedMapping, q1, q2, dataDir);

        printComparison("Parent TriplesMap Subject Change", records, burpNoCache, burpStats, carmlStats);

        assertThat(burpStats.hitsDeltaRun2()).isGreaterThan(0);
        assertThat(burpNoCache.allSparqlPassed()).isTrue();
        assertThat(burpStats.allSparqlPassed()).isTrue();
        assertThat(carmlStats.allSparqlPassed()).isTrue();
    }

    @Test
    public void testLogicalViewFieldChangeIncremental() throws Exception {
        int records = getRecordsCount();
        Path dataDir = Path.of("src/test/resources/perf/incremental/logical_view_field_change");
        IncrementalDataGenerator.generateLogicalViewData(dataDir, records);

        String baseMapping = dataDir.resolve("mapping_base.ttl").toString();
        String modifiedMapping = dataDir.resolve("mapping_modified.ttl").toString();
        String q1 = dataDir.resolve("verify_run1.rq").toString();
        String q2 = dataDir.resolve("verify_run2.rq").toString();

        BenchmarkStats burpNoCache = runBurpNoCacheScenario(baseMapping, modifiedMapping, q1, q2);
        BenchmarkStats burpStats = runBurpScenario(baseMapping, modifiedMapping, q1, q2);
        BenchmarkStats carmlStats = runCarmlScenario(baseMapping, modifiedMapping, q1, q2, dataDir);

        printComparison("Logical View Field Column Change", records, burpNoCache, burpStats, carmlStats);

        assertThat(burpStats.hitsDeltaRun2()).isGreaterThan(0);
        assertThat(burpNoCache.allSparqlPassed()).isTrue();
        assertThat(burpStats.allSparqlPassed()).isTrue();
        assertThat(carmlStats.allSparqlPassed()).isTrue();
    }

    @Test
    public void testPomPredicateChangeIncremental() throws Exception {
        int records = getRecordsCount();
        Path dataDir = Path.of("src/test/resources/perf/incremental/pom_predicate_change");
        IncrementalDataGenerator.generatePomPredicateChangeData(dataDir, records);

        String baseMapping = dataDir.resolve("mapping_base.ttl").toString();
        String modifiedMapping = dataDir.resolve("mapping_modified.ttl").toString();
        String q1 = dataDir.resolve("verify_run1.rq").toString();
        String q2 = dataDir.resolve("verify_run2.rq").toString();

        BenchmarkStats burpNoCache = runBurpNoCacheScenario(baseMapping, modifiedMapping, q1, q2);
        BenchmarkStats burpStats = runBurpScenario(baseMapping, modifiedMapping, q1, q2);
        BenchmarkStats carmlStats = runCarmlScenario(baseMapping, modifiedMapping, q1, q2, dataDir);

        printComparison("POM Predicate Change", records, burpNoCache, burpStats, carmlStats);

        assertThat(burpStats.hitsDeltaRun2()).isGreaterThan(0);
        assertThat(burpNoCache.allSparqlPassed()).isTrue();
        assertThat(burpStats.allSparqlPassed()).isTrue();
        assertThat(carmlStats.allSparqlPassed()).isTrue();
    }

    private static long getUsedMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private Model convertBurpToJenaModel(List<EvaluateResult> results) {
        Model model = ModelFactory.createDefaultModel();
        for (EvaluateResult res : results) {
            for (RmlEvaluationResult r : res.generatedRdf()) {
                if (r instanceof RdfStatement st) {
                    Resource s = st.getSubject() instanceof IRITerm iri ? model.createResource(iri.uri()) : model.createResource(new AnonId(((BlankNodeTerm) st.getSubject()).id()));
                    Property p = model.createProperty(st.getPredicate().uri());
                    RDFNode o;
                    if (st.getObject() instanceof IRITerm iri) {
                        o = model.createResource(iri.uri());
                    } else if (st.getObject() instanceof BlankNodeTerm bnode) {
                        o = model.createResource(new AnonId(bnode.id()));
                    } else if (st.getObject() instanceof LiteralTerm lit) {
                        if (lit.language() != null && !lit.language().isBlank()) {
                            o = model.createLiteral(lit.value(), lit.language());
                        } else if (lit.datatype() != null && lit.datatype().uri() != null && !lit.datatype().uri().isBlank()) {
                            o = model.createTypedLiteral(lit.value(), TypeMapper.getInstance().getSafeTypeByName(lit.datatype().uri()));
                        } else {
                            o = model.createLiteral(lit.value());
                        }
                    } else {
                        o = model.createLiteral(st.getObject().toString());
                    }
                    model.add(s, p, o);
                }
            }
        }
        return model;
    }

    private Model convertRdf4jToJenaModel(org.eclipse.rdf4j.model.Model rdf4jModel) {
        Model jenaModel = ModelFactory.createDefaultModel();
        for (org.eclipse.rdf4j.model.Statement st : rdf4jModel) {
            Resource s = st.getSubject().isBNode() ? jenaModel.createResource(new AnonId(st.getSubject().stringValue())) : jenaModel.createResource(st.getSubject().stringValue());
            Property p = jenaModel.createProperty(st.getPredicate().stringValue());
            RDFNode o;
            if (st.getObject().isIRI()) {
                o = jenaModel.createResource(st.getObject().stringValue());
            } else if (st.getObject().isBNode()) {
                o = jenaModel.createResource(new AnonId(st.getObject().stringValue()));
            } else {
                org.eclipse.rdf4j.model.Literal lit = (org.eclipse.rdf4j.model.Literal) st.getObject();
                if (lit.getLanguage().isPresent()) {
                    o = jenaModel.createLiteral(lit.getLabel(), lit.getLanguage().get());
                } else if (lit.getDatatype() != null) {
                    o = jenaModel.createTypedLiteral(lit.getLabel(), TypeMapper.getInstance().getSafeTypeByName(lit.getDatatype().stringValue()));
                } else {
                    o = jenaModel.createLiteral(lit.getLabel());
                }
            }
            jenaModel.add(s, p, o);
        }
        return jenaModel;
    }

    private boolean verifyWithSparql(Model model, String sparqlPath) throws IOException {
        String queryString = Files.readString(Path.of(sparqlPath));
        Query query = QueryFactory.create(queryString);
        try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
            if (query.isAskType()) {
                return qexec.execAsk();
            } else if (query.isSelectType()) {
                ResultSet rs = qexec.execSelect();
                return rs.hasNext();
            }
        }
        return true;
    }
}

