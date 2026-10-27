package burp.dev;

import burp.dev.rml.service.eval.AllDataContext;
import burp.dev.rml.service.eval.EvaluateParams;
import burp.dev.rml.service.eval.EvaluateResult;
import burp.dev.rml.service.eval.RangeDataContext;
import burp.dev.rml.service.selector.IdSelector;
import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.eval.RmlEvaluator;
import burp.dev.rml.service.cache.DevServerCache;
import burp.model.PlanNode;
import burp.model.PlanNodeIdRegistry;
import burp.model.RmlEvaluationResult;
import burp.model.rdf.LiteralTerm;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CacheTest {

    @Test
    public void testDevServerCacheMemoizationTier() {
        DevServerCache cache = new DevServerCache();
        burp.model.RDFNodeConstant constantNode = new burp.model.RDFNodeConstant(new LiteralTerm("ConstantVal"));

        // Initially cache miss
        assertThat(cache.getEvaluation(constantNode, 42)).isNull();

        // Populate cache
        List<RmlEvaluationResult> expected = List.of(new LiteralTerm("ConstantVal"));
        cache.putEvaluation(constantNode, 42, expected);

        // Cache hit
        assertThat(cache.getEvaluation(constantNode, 42)).isEqualTo(expected);

        // Invalidate all
        cache.invalidateAll();
        assertThat(cache.getEvaluation(constantNode, 42)).isNull();
    }

    @Test
    public void testRmlEvaluatorUsesCacheAcrossInvocations() throws Exception {
        String uri = "src/test/resources/burp/eval_alice/mapping.ttl";
        MappingWorkspace workspace = new MappingWorkspace();
        DevServerCache cache = workspace.getCache();

        var doc = workspace.loadMappingWithStableIds(uri);
        assertThat(doc.triplesMaps).isNotEmpty();

        PlanNode targetNode = doc.triplesMaps.getFirst().subjectMap;
        assertThat(targetNode).isNotNull();

        String nodeId = PlanNodeIdRegistry.getId(targetNode);
        EvaluateParams params = new EvaluateParams(new IdSelector(uri, nodeId), new AllDataContext());

        RmlEvaluator evaluator = new RmlEvaluator(cache);

        // First evaluate populates cache
        List<EvaluateResult> r1 = evaluator.evaluate(params, workspace).get();
        assertThat(r1).isNotEmpty();

        // Verify that sample iterations tier was populated
        assertThat(cache.getIterations(doc.triplesMaps.getFirst().logicalSource, new AllDataContext())).isNotNull();

        // Second evaluate hits cache and returns identical results
        List<EvaluateResult> r2 = evaluator.evaluate(params, workspace).get();
        assertThat(r2).isEqualTo(r1);
    }

    @Test
    public void testIncrementalEvaluationBenchmark() throws Exception {
        String uri = "src/test/resources/burp/rml_tc0009a/mapping.ttl";
        MappingWorkspace workspace = new MappingWorkspace();
        DevServerCache cache = workspace.getCache();
        RmlEvaluator evaluator = new RmlEvaluator(cache);

        // WARMUP (to prevent JVM JIT compilation from skewing the timing of Run 1)
        var warmupDoc = workspace.loadMappingWithStableIds(uri);
        PlanNode warmupTargetNode = warmupDoc.triplesMaps.getFirst();
        String warmupNodeId = PlanNodeIdRegistry.getId(warmupTargetNode);
        for (int i = 0; i < 5; i++) {
            evaluator.evaluate(new EvaluateParams(new IdSelector(uri, warmupNodeId), new AllDataContext()), workspace).get();
        }
        cache.invalidateAll(); // Clear cache before real benchmark

        // 1. Initial Evaluation (Run 1)
        var doc = workspace.loadMappingWithStableIds(uri);
        PlanNode targetNode = doc.triplesMaps.getFirst();
        String nodeId = PlanNodeIdRegistry.getId(targetNode);
        EvaluateParams params = new EvaluateParams(new IdSelector(uri, nodeId), new AllDataContext());

        long start1 = System.nanoTime();
        List<EvaluateResult> r1 = evaluator.evaluate(params, workspace).get();
        long duration1 = System.nanoTime() - start1;

        long initialHitCount = cache.getHitCount();
        long initialMissCount = cache.getMissCount();

        assertThat(r1).isNotEmpty();
        assertThat(initialMissCount).isGreaterThan(0);

        // 2. Modify an unrelated POM in the document text
        String originalText = workspace.getOpenDocumentText(uri);
        if (originalText == null) {
            originalText = Files.readString(workspace.normalizePath(uri));
        }
        // Changing "$.Name" to "$.FullName" affects one POM, but NOT the LogicalSource or the Join
        String modifiedText = originalText.replace("$.Name", "$.FullName");
        workspace.updateDocumentText(uri, modifiedText);

        // Reload mapping to get new AST nodes
        var doc2 = workspace.loadMappingWithStableIds(uri);
        PlanNode targetNode2 = doc2.triplesMaps.getFirst();
        String nodeId2 = PlanNodeIdRegistry.getId(targetNode2);
        EvaluateParams params2 = new EvaluateParams(new IdSelector(uri, nodeId2), new AllDataContext());

        // 3. Second Evaluation (Run 2)
        long start2 = System.nanoTime();
        List<EvaluateResult> r2 = evaluator.evaluate(params2, workspace).get();
        long duration2 = System.nanoTime() - start2;

        assertThat(r2).isNotEmpty();

        long finalHitCount = cache.getHitCount();

        // Assertions requested by user
        System.out.println("Run 1: " + duration1 + "ns, Run 2: " + duration2 + "ns");

        // Verify cache hit (reusing cached parent join index / iterations)
        assertThat(finalHitCount).isGreaterThan(initialHitCount);
    }

    @Test
    public void testDevServerCacheNoRangePoisoning() throws Exception {
        String uri = "src/test/resources/burp/eval_alice/mapping.ttl";
        MappingWorkspace workspace = new MappingWorkspace();
        DevServerCache cache = workspace.getCache();
        RmlEvaluator evaluator = new RmlEvaluator(cache);

        var doc = workspace.loadMappingWithStableIds(uri);
        var ls = doc.triplesMaps.getFirst().logicalSource;
        assertThat(ls).isNotNull();

        // 1. Evaluate only range [0-0] (preview first iteration)
        List<burp.model.Iteration> range0 = evaluator.resolveIterations(new RangeDataContext(0, 0), ls);
        assertThat(range0).hasSize(1);
        assertThat(range0.getFirst().getId()).isNotNull();
        assertThat(range0.getFirst().getId().index()).isEqualTo(0);

        // 2. Full execution requesting all iterations should NOT get poisoned by range [0-0]
        List<burp.model.Iteration> allIters = evaluator.resolveIterations(new AllDataContext(), ls);
        assertThat(allIters.size()).isGreaterThan(1);

        // 3. Requesting range [1-1] should return row 1 with correct index
        List<burp.model.Iteration> range1 = evaluator.resolveIterations(new RangeDataContext(1, 1), ls);
        assertThat(range1).hasSize(1);
        assertThat(range1.getFirst().getId()).isNotNull();
        assertThat(range1.getFirst().getId().index()).isEqualTo(1);
    }
}
