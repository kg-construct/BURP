package burp.dev.rml.service.trace;

import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.eval.RmlEvaluator;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.CompletableFuture;

/**
 * Handles rml/trace requests by delegating to {@link TracePipelineEngine}.
 */
@NullMarked
public class RmlTracer {

    private final TracePipelineEngine engine;

    public RmlTracer() {
        this(new TracePipelineEngine());
    }

    public RmlTracer(TracePipelineEngine engine) {
        this.engine = engine;
    }

    public CompletableFuture<TraceResult> trace(TraceParams params, MappingWorkspace workspace, RmlEvaluator evaluator) {
        return engine.trace(params, workspace, evaluator);
    }

    public CompletableFuture<TraceResult> traceIteration(TraceIterationParams params, MappingWorkspace workspace, RmlEvaluator evaluator) {
        return engine.traceIteration(params, workspace, evaluator);
    }

    public CompletableFuture<TraceResult> traceResult(TraceResultParams params, MappingWorkspace workspace, RmlEvaluator evaluator) {
        return engine.traceResult(params, workspace, evaluator);
    }
}
