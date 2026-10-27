package burp.dev.rml.service.trace;

import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.eval.*;
import burp.model.IterationId;
import burp.model.OriginLineage;
import org.jspecify.annotations.NullMarked;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/// High-level facade coordinating trace evaluation, backward provenance slicing, and serialization.
///
/// ### Architecture
///
/// Acts as the single entrypoint for LSP trace requests:
///
/// * [`trace`]: Full forward trace across iterations for a target AST component.
/// * [`traceIteration`]: Focused trace on a single 0-indexed iteration.
/// * [`traceResult`]: Targeted backward slice isolating the provenance of a specific output triple.
///
/// Delegates to:
/// * [`TraceExecutor`] for graph construction and AST evaluation.
/// * [`TraceSlicer`] for backward BFS provenance pruning.
/// * [`TraceGraphExporter`] for LSP DTO and JSON-LD conversion.
@NullMarked
public class TracePipelineEngine {

    public CompletableFuture<TraceResult> trace(TraceParams params, MappingWorkspace workspace, RmlEvaluator evaluator) {
        try {
            TraceGraph fullGraph = TraceExecutor.execute(params, workspace, evaluator);
            String mappingFileUri = workspace.getMappingFileUri(params.structure());
            Map<String, String> prefixMap = workspace.getPrefixMap(mappingFileUri);
            TraceResult result = TraceGraphExporter.export(fullGraph, prefixMap);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<TraceResult> traceIteration(TraceIterationParams params, MappingWorkspace workspace, RmlEvaluator evaluator) {
        TraceParams tp = new TraceParams(
                params.structure(),
                new RangeDataContext(params.iterationIndex(), params.iterationIndex() + 1, List.of(params.iterationIndex()))
        );
        return trace(tp, workspace, evaluator);
    }

    public CompletableFuture<TraceResult> traceResult(TraceResultParams params, MappingWorkspace workspace, RmlEvaluator evaluator) {
        try {
            EvaluationDataContext dataContext;
            if (params.lineage() != null) {
                Map<String, EvaluationDataContext> sourceContexts = new HashMap<>();
                OriginLineage lineage = params.lineage();
                sourceContexts.put(lineage.primary().logicalSourceId(), new RangeDataContext(lineage.primary().index(), lineage.primary().index(), List.of(lineage.primary().index())));
                for (IterationId joined : lineage.joined()) {
                    sourceContexts.put(joined.logicalSourceId(), new RangeDataContext(joined.index(), joined.index(), List.of(joined.index())));
                }
                dataContext = new MultiSourceDataContext(sourceContexts, null);
            } else {
                dataContext = new AllDataContext();
            }

            TraceParams tp = new TraceParams(params.structure(), dataContext);
            TraceGraph fullGraph = TraceExecutor.execute(tp, workspace, evaluator);
            TraceGraph slicedGraph = TraceSlicer.slice(fullGraph, params);

            String mappingFileUri = workspace.getMappingFileUri(params.structure());
            Map<String, String> prefixMap = workspace.getPrefixMap(mappingFileUri);
            TraceResult result = TraceGraphExporter.export(slicedGraph, prefixMap);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
