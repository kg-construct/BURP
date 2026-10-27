package burp.dev.rml.service.cache;

import burp.dev.rml.service.eval.AllDataContext;
import burp.dev.rml.service.eval.CustomDataContext;
import burp.dev.rml.service.eval.EvaluationDataContext;
import burp.dev.rml.service.eval.RangeDataContext;
import burp.model.AbstractLogicalSource;
import burp.model.Iteration;
import burp.model.PlanNode;
import burp.model.RmlEvaluationResult;
import burp.model.join.ParentJoinIndex;
import burp.model.key.AstKey;
import burp.dev.rml.service.schema.SqlSchemaProvider.CachedSchema;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Multi-tier incremental cache for the RML Dev Server.
 * Supports:
 * 1. Sample Iterations Cache: (LogicalSource hash, DataContext) -> List<Iteration>
 * 2. Parent Join Index Cache: (Referencing node hash, Parent LogicalSource hash, DataContext) -> ParentJoinIndex
 * 3. Node Evaluation Memoization: (Node Merkle hash, Iteration identifier/index) -> List<RmlEvaluationResult>
 * 4. Database metadata cache: (SQL query, DataContext hash) -> CachedSchema
 */
@NullMarked
public class DevServerCache {

    // Dynamically scale cache bounds based on JVM heap capacity
    private static final long MAX_HEAP_BYTES = Runtime.getRuntime().maxMemory();
    private static final long SOURCE_ITER_MAX_SIZE = Math.max(500L, MAX_HEAP_BYTES / (2L * 1024L * 1024L));
    private static final long PARENT_JOIN_MAX_SIZE = Math.max(200L, MAX_HEAP_BYTES / (4L * 1024L * 1024L));
    private static final long NODE_MEMO_MAX_SIZE = Math.max(100_000L, MAX_HEAP_BYTES / 2048L);

    // Tier 1: Source Iterations (Cached softly so GC can reclaim if RAM is exhausted)
    public record CustomSourceCacheKey(AstKey sourceKey, CustomDataContext customContext) {
    }

    private final Cache<AstKey, List<Iteration>> sourceIterationCache = CacheBuilder.newBuilder()
            .maximumSize(SOURCE_ITER_MAX_SIZE)
            .softValues()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .recordStats()
            .build();

    private final Cache<CustomSourceCacheKey, List<Iteration>> customIterationCache = CacheBuilder.newBuilder()
            .maximumSize(SOURCE_ITER_MAX_SIZE)
            .softValues()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .recordStats()
            .build();

    // Tier 2: Parent Join Index
    public record ParentJoinCacheKey(AstKey nodeKey, AstKey parentSourceKey,
                                     @Nullable EvaluationDataContext dataContext) {
    }

    private final Cache<ParentJoinCacheKey, ParentJoinIndex> parentJoinCache = CacheBuilder.newBuilder()
            .maximumSize(PARENT_JOIN_MAX_SIZE)
            .softValues()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .recordStats()
            .build();

    // Tier 3: Node Evaluation Memoization
    public record NodeMemoCacheKey(AstKey nodeKey, int iterationKey) {
    }

    private final Cache<NodeMemoCacheKey, List<RmlEvaluationResult>> nodeMemoCache = CacheBuilder.newBuilder()
            .maximumSize(NODE_MEMO_MAX_SIZE)
            .softValues()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .recordStats()
            .build();

    // Tier 4: SQL Schema Cache
    private final Cache<String, CachedSchema> sqlSchemaCache = CacheBuilder.newBuilder()
            .maximumSize(500)
            .softValues()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .recordStats()
            .build();

    public long getHitCount() {
        return getStats().hitCount();
    }

    public long getMissCount() {
        return getStats().missCount();
    }

    public CacheStats getStats() {
        return sourceIterationCache.stats()
                .plus(customIterationCache.stats())
                .plus(parentJoinCache.stats())
                .plus(nodeMemoCache.stats())
                .plus(sqlSchemaCache.stats());
    }

    public @Nullable List<Iteration> getIterations(AbstractLogicalSource source) {
        return sourceIterationCache.getIfPresent(source.getAstKey());
    }

    public @Nullable List<Iteration> getIterations(AbstractLogicalSource source, EvaluationDataContext dataContext) {
        AstKey sourceKey = source.getAstKey();

        if (dataContext instanceof CustomDataContext cdc) {
            return customIterationCache.getIfPresent(new CustomSourceCacheKey(sourceKey, cdc));
        }

        List<Iteration> fullList = sourceIterationCache.getIfPresent(sourceKey);
        if (fullList != null) {
            if (dataContext instanceof AllDataContext) {
                return fullList;
            }
            if (dataContext instanceof RangeDataContext rdc) {
                return sliceIterations(fullList, rdc);
            }
        }

        return null;
    }

    private List<Iteration> sliceIterations(List<Iteration> fullList, RangeDataContext rdc) {
        List<Integer> specific = rdc.specificIndices();
        if (specific != null && !specific.isEmpty()) {
            List<Iteration> res = new ArrayList<>();
            for (int idx : specific) {
                if (idx >= 0 && idx < fullList.size()) {
                    res.add(fullList.get(idx));
                }
            }
            return res;
        }
        int start = Math.max(0, rdc.startIndex());
        int end = Math.min(fullList.size(), rdc.endIndex() + 1);
        if (start >= fullList.size() || start >= end) {
            return Collections.emptyList();
        }
        return new ArrayList<>(fullList.subList(start, end));
    }

    public void putIterations(AbstractLogicalSource source, List<Iteration> iterations) {
        sourceIterationCache.put(source.getAstKey(), iterations);
    }

    public void putIterations(AbstractLogicalSource source, EvaluationDataContext dataContext, List<Iteration> iterations) {
        AstKey sourceKey = source.getAstKey();
        if (dataContext instanceof CustomDataContext cdc) {
            customIterationCache.put(new CustomSourceCacheKey(sourceKey, cdc), iterations);
        } else {
            sourceIterationCache.put(sourceKey, iterations);
        }
    }

    public @Nullable ParentJoinIndex getParentJoinIndex(PlanNode referencingNode, AbstractLogicalSource parentSource) {
        return parentJoinCache.getIfPresent(new ParentJoinCacheKey(referencingNode.getAstKey(), parentSource.getAstKey(), null));
    }

    public @Nullable ParentJoinIndex getParentJoinIndex(PlanNode referencingNode, AbstractLogicalSource parentSource, EvaluationDataContext dataContext) {
        AstKey nodeKey = referencingNode.getAstKey();
        AstKey pSourceKey = parentSource.getAstKey();

        ParentJoinIndex fullIndex = parentJoinCache.getIfPresent(new ParentJoinCacheKey(nodeKey, pSourceKey, null));
        if (fullIndex != null) {
            return fullIndex;
        }

        EvaluationDataContext keyContext = (dataContext instanceof AllDataContext) ? null : dataContext;
        return parentJoinCache.getIfPresent(new ParentJoinCacheKey(nodeKey, pSourceKey, keyContext));
    }

    public void putParentJoinIndex(PlanNode referencingNode, AbstractLogicalSource parentSource, ParentJoinIndex index) {
        parentJoinCache.put(new ParentJoinCacheKey(referencingNode.getAstKey(), parentSource.getAstKey(), null), index);
    }

    public void putParentJoinIndex(PlanNode referencingNode, AbstractLogicalSource parentSource, EvaluationDataContext dataContext, ParentJoinIndex index) {
        AstKey nodeKey = referencingNode.getAstKey();
        AstKey pSourceKey = parentSource.getAstKey();
        EvaluationDataContext keyContext = (dataContext instanceof AllDataContext) ? null : dataContext;
        parentJoinCache.put(new ParentJoinCacheKey(nodeKey, pSourceKey, keyContext), index);
    }

    public @Nullable List<RmlEvaluationResult> getEvaluation(AstKey nodeKey, int iterationId) {
        return nodeMemoCache.getIfPresent(new NodeMemoCacheKey(nodeKey, iterationId));
    }

    public @Nullable List<RmlEvaluationResult> getEvaluation(PlanNode node, int iterationId) {
        return getEvaluation(node.getAstKey(), iterationId);
    }

    public void putEvaluation(AstKey nodeKey, int iterationId, List<RmlEvaluationResult> results) {
        nodeMemoCache.put(new NodeMemoCacheKey(nodeKey, iterationId), results);
    }

    public void putEvaluation(PlanNode node, int iterationId, List<RmlEvaluationResult> results) {
        putEvaluation(node.getAstKey(), iterationId, results);
    }

    public @Nullable CachedSchema getSqlSchema(String key) {
        return sqlSchemaCache.getIfPresent(key);
    }

    public void putSqlSchema(String key, CachedSchema schema) {
        sqlSchemaCache.put(key, schema);
    }

    public void removeSqlSchema(String key) {
        sqlSchemaCache.invalidate(key);
    }

    public CacheStats getParentJoinStats() {
        return parentJoinCache.stats();
    }

    public long getParentJoinCacheSize() {
        return parentJoinCache.size();
    }

    public CacheStats getNodeMemoStats() {
        return nodeMemoCache.stats();
    }

    public long getNodeMemoCacheSize() {
        return nodeMemoCache.size();
    }

    public CacheStats getSqlSchemaStats() {
        return sqlSchemaCache.stats();
    }

    public long getSqlSchemaCacheSize() {
        return sqlSchemaCache.size();
    }

    public CacheStats getSourceIterationCacheStats() {
        return sourceIterationCache.stats();
    }

    public long getSourceIterationCacheSize() {
        return sourceIterationCache.size();
    }


    /**
     * Clears all cache.
     */
    public void invalidateAll() {
        sourceIterationCache.invalidateAll();
        customIterationCache.invalidateAll();
        parentJoinCache.invalidateAll();
        nodeMemoCache.invalidateAll();
        sqlSchemaCache.invalidateAll();
    }
}
