package burp.dev.rml;

import burp.dev.rml.service.eval.*;
import burp.dev.rml.service.execute.ExecuteParams;
import burp.dev.rml.service.execute.ExecuteResult;
import burp.dev.rml.service.plan.PlanParams;
import burp.dev.rml.service.plan.PlanResult;
import burp.dev.rml.service.plan.ReferenceDependency;
import burp.dev.rml.service.plan.ReferencesDependenciesParams;
import burp.dev.rml.service.source.SourceDataParams;
import burp.dev.rml.service.source.SourceDataResult;
import burp.dev.rml.service.trace.TraceIterationParams;
import burp.dev.rml.service.trace.TraceParams;
import burp.dev.rml.service.trace.TraceResult;
import burp.dev.rml.service.trace.TraceResultParams;
import burp.dev.rml.service.validator.RmlDiagnostic;
import burp.dev.rml.service.validator.ValidateParams;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageServer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface RmlLanguageServer extends LanguageServer {

    /**
     * Executes the mapping with execution priority given to a specific structure.
     */
    @JsonRequest("rml/execute")
    CompletableFuture<ExecuteResult> execute(ExecuteParams params);

    /**
     * Clears all DevServerCache tiers (source iterations, parent join index, node memoization).
     */
    @JsonRequest("rml/clearCache")
    CompletableFuture<Void> clearCache();

    /**
     * Evaluates a mapping structure against a data context and returns the generated RDF terms or statements.
     */
    @JsonRequest("rml/evaluate")
    CompletableFuture<List<EvaluateResult>> evaluate(EvaluateParams params);

    /**
     * Returns the reference formulation dependencies (e.g. fields, JSONPaths) required by a structure.
     */
    @JsonRequest("rml/referencesDependencies")
    CompletableFuture<List<ReferenceDependency>> referencesDependencies(ReferencesDependenciesParams params);


    /**
     * Retrieves the complete execution plan tree of the mapping document,
     * optionally resolving and identifying the active node matching the given structure selector.
     */
    @JsonRequest("rml/plan")
    CompletableFuture<PlanResult> plan(PlanParams params);

    /**
     * Traces the data flow and execution graph for a structure and data context.
     */
    @JsonRequest("rml/trace")
    CompletableFuture<TraceResult> trace(TraceParams params);

    /**
     * Traces the forward data flow and execution graph for a single iteration.
     */
    @JsonRequest("rml/traceIteration")
    CompletableFuture<TraceResult> traceIteration(TraceIterationParams params);

    /**
     * Traces the backward provenance slicing graph for a specific generated result quad.
     */
    @JsonRequest("rml/traceResult")
    CompletableFuture<TraceResult> traceResult(TraceResultParams params);

    /**
     * Evaluates all non-constant mapping expressions across a document in a single query,
     * returning semantic AST evaluation items with source ranges and compacted RDF terms.
     */
    @JsonRequest("rml/inlineEvaluate")
    CompletableFuture<List<InlineEvaluateItem>> inlineEvaluate(InlineEvaluateParams params);

    /**
     * Statically validates the mapping document, checking references, templates, function parameters,
     * unused RML elements, and shape conformance.
     */
    @JsonRequest("rml/validate")
    CompletableFuture<List<RmlDiagnostic>> validate(ValidateParams params);

    /**
     * Steps the active inline evaluation iteration for the anchor TriplesMap referenced by structure.
     */
    @JsonRequest("rml/stepInlineIteration")
    CompletableFuture<StepInlineIterationResult> stepInlineIteration(StepInlineIterationParams params);

    /**
     * Retrieves a windowed tabular or document dataset from a Logical Source or Logical View.
     */
    @JsonRequest("rml/source/data")
    CompletableFuture<SourceDataResult> getSourceData(SourceDataParams params);
}

