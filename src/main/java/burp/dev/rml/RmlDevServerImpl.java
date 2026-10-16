package burp.dev.rml;

import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.eval.*;
import burp.dev.rml.service.execute.ExecuteParams;
import burp.dev.rml.service.execute.ExecuteResult;
import burp.dev.rml.service.execute.RmlExecutionService;
import burp.dev.rml.service.source.SourceDataInspector;
import burp.dev.rml.service.source.SourceDataParams;
import burp.dev.rml.service.source.SourceDataResult;
import burp.dev.rml.service.plan.*;
import burp.dev.rml.service.trace.*;
import burp.dev.rml.service.validator.RmlDiagnostic;
import burp.dev.rml.service.validator.StaticMappingValidator;
import burp.dev.rml.service.validator.ValidateParams;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of the custom RML development protocol server.
 * Coordinates requests across dedicated domain services.
 */
public class RmlDevServerImpl implements RmlLanguageServer, LanguageClientAware {

    private final MappingWorkspace workspace;
    private final RmlEvaluator evaluator;
    private final RmlTracer tracer;
    private final PlanInspector planInspector;
    private final InlineEvaluator inlineEvaluator;
    private final StaticMappingValidator validator;
    private final SourceDataInspector logicalViewInspector;
    private final RmlExecutionService executionService = new RmlExecutionService();
    private @Nullable LanguageClient client;

    public RmlDevServerImpl() {
        this(new MappingWorkspace());
    }

    public RmlDevServerImpl(MappingWorkspace workspace) {
        this(workspace, new RmlEvaluator(workspace.getCache()), new RmlTracer(), new PlanInspector(), new InlineEvaluator(), new StaticMappingValidator(), new SourceDataInspector());
    }

    public RmlDevServerImpl(
            MappingWorkspace workspace,
            RmlEvaluator evaluator,
            RmlTracer tracer,
            PlanInspector planInspector,
            InlineEvaluator inlineEvaluator,
            StaticMappingValidator validator,
            SourceDataInspector logicalViewInspector
    ) {
        this.workspace = workspace;
        this.evaluator = evaluator;
        this.tracer = tracer;
        this.planInspector = planInspector;
        this.inlineEvaluator = inlineEvaluator;
        this.validator = validator;
        this.logicalViewInspector = logicalViewInspector;
    }

    public MappingWorkspace getWorkspace() {
        return workspace;
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
        workspace.connect(client);
    }

    public void pullConfigurationFromClient() {
        workspace.pullConfigurationFromClient();
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        if (params.getInitializationOptions() != null) {
            workspace.updateSettings(params.getInitializationOptions());
        }
        if (params.getRootUri() != null && !params.getRootUri().isBlank()) {
            try {
                URI rootUri = URI.create(params.getRootUri());
                workspace.setCurrentWorkingDirectory(Path.of(rootUri));
            } catch (Exception ignored) {
            }
        } else if (params.getRootPath() != null && !params.getRootPath().isBlank()) {
            workspace.setCurrentWorkingDirectory(Path.of(params.getRootPath()));
        } else if (params.getWorkspaceFolders() != null && !params.getWorkspaceFolders().isEmpty()) {
            try {
                URI rootUri = URI.create(params.getWorkspaceFolders().getFirst().getUri());
                workspace.setCurrentWorkingDirectory(Path.of(rootUri));
            } catch (Exception ignored) {
            }
        }
        ServerCapabilities capabilities = new ServerCapabilities();
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
        CompletionOptions completionOptions = new CompletionOptions();
        completionOptions.setTriggerCharacters(List.of("\"", "$", "{", "}", "/", ":", ".", "_", "@"));
        capabilities.setCompletionProvider(completionOptions);
        capabilities.setInlayHintProvider(new InlayHintRegistrationOptions());

        InitializeResult result = new InitializeResult(capabilities);
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return workspace.getTextDocumentService();
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspace;
    }

    @Override
    public CompletableFuture<ExecuteResult> execute(ExecuteParams params) {
        return executionService.execute(params, workspace, client);
    }

    @Override
    public CompletableFuture<Void> clearCache() {
        workspace.getCache().invalidateAll();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<EvaluateResult>> evaluate(EvaluateParams params) {
        return evaluator.evaluate(params, workspace);
    }

    @Override
    public CompletableFuture<TraceResult> trace(TraceParams params) {
        return tracer.trace(params, workspace, evaluator);
    }

    @Override
    public CompletableFuture<TraceResult> traceIteration(TraceIterationParams params) {
        return tracer.traceIteration(params, workspace, evaluator);
    }

    @Override
    public CompletableFuture<TraceResult> traceResult(TraceResultParams params) {
        return tracer.traceResult(params, workspace, evaluator);
    }

    @Override
    public CompletableFuture<List<ReferenceDependency>> referencesDependencies(ReferencesDependenciesParams params) {
        return planInspector.referencesDependencies(params, workspace);
    }

    @Override
    public CompletableFuture<PlanResult> plan(PlanParams params) {
        return planInspector.plan(params, workspace);
    }


    @Override
    public CompletableFuture<List<InlineEvaluateItem>> inlineEvaluate(InlineEvaluateParams params) {
        return inlineEvaluator.inlineEvaluate(params, workspace, evaluator);
    }

    @Override
    public CompletableFuture<List<RmlDiagnostic>> validate(ValidateParams params) {
        return validator.validate(params, workspace);
    }

    @Override
    public CompletableFuture<SourceDataResult> getSourceData(SourceDataParams params) {
        return logicalViewInspector.getSourceData(params, workspace);
    }
}
