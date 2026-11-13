package burp.dev.rml;

import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.validator.RmlValidationService;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@NullMarked
public class LspDiagnosticsLifecycleTest {

    private RmlDevServerImpl server = new RmlDevServerImpl();
    private MappingWorkspace workspace = server.getWorkspace();
    private TestLanguageClient client = new TestLanguageClient();

    static class TestLanguageClient implements LanguageClient {
        final List<PublishDiagnosticsParams> publishedDiagnostics = new CopyOnWriteArrayList<>();
        final List<MessageParams> showMessages = new CopyOnWriteArrayList<>();
        final List<MessageParams> logMessages = new CopyOnWriteArrayList<>();

        @Override
        public void telemetryEvent(Object object) {}

        @Override
        public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
            publishedDiagnostics.add(diagnostics);
        }

        @Override
        public void showMessage(MessageParams messageParams) {
            showMessages.add(messageParams);
        }

        @Override
        public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void logMessage(MessageParams message) {
            logMessages.add(message);
        }
    }

    @BeforeEach
    public void setUp() {
        server = new RmlDevServerImpl();
        workspace = server.getWorkspace();
        client = new TestLanguageClient();
        server.connect(client);
    }

    @AfterEach
    public void tearDown() {
        server.shutdown();
    }

    private String toUri(String path) {
        return new File(path).getAbsoluteFile().toURI().toString();
    }

    @Test
    public void testDidOpenPublishesTier1AndTier2() throws Exception {
        String path = "src/test/resources/burp/validate/invalid-sql-col.ttl";
        String uri = toUri(path);
        String text = Files.readString(Path.of(path));

        TextDocumentItem item = new TextDocumentItem(uri, "turtle", 1, text);
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(item));

        // Wait up to 2 seconds for Tier 2 async task to finish
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 2000) {
            List<Diagnostic> latest = workspace.getValidationService().getLastPublishedDiagnostics(uri);
            boolean hasDynamic = latest.stream().anyMatch(d -> {
                String msg = d.getMessage() != null && d.getMessage().isLeft() ? d.getMessage().getLeft() : "";
                return msg.contains("non_existent_col");
            });
            if (hasDynamic) {
                break;
            }
            Thread.sleep(50);
        }

        List<Diagnostic> published = workspace.getValidationService().getLastPublishedDiagnostics(uri);
        assertThat(published).isNotEmpty();
        assertThat(published).anyMatch(d -> {
            String msg = d.getMessage() != null && d.getMessage().isLeft() ? d.getMessage().getLeft() : "";
            return msg.contains("non_existent_col") && !msg.startsWith(RmlValidationService.REVALIDATING_PREFIX);
        });
    }

    @Test
    public void testDidChangeTagsDynamicWithRevalidatingThenClearsAfterDebounce() throws Exception {
        String path = "src/test/resources/burp/validate/invalid-sql-col.ttl";
        String uri = toUri(path);
        String text = Files.readString(Path.of(path));

        workspace.getValidationService().setDebounceDelayMs(150L);

        // 1. Initial open
        TextDocumentItem item = new TextDocumentItem(uri, "turtle", 1, text);
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(item));

        // Wait for initial dynamic validation
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 2000) {
            List<Diagnostic> latest = workspace.getValidationService().getLastPublishedDiagnostics(uri);
            boolean hasDynamic = latest.stream().anyMatch(d -> {
                String msg = d.getMessage() != null && d.getMessage().isLeft() ? d.getMessage().getLeft() : "";
                return msg.contains("non_existent_col");
            });
            if (hasDynamic) break;
            Thread.sleep(50);
        }

        // Verify initial state has no [revalidating...]
        List<Diagnostic> beforeEdit = workspace.getValidationService().getLastPublishedDiagnostics(uri);
        assertThat(beforeEdit).anyMatch(d -> {
            String msg = d.getMessage() != null && d.getMessage().isLeft() ? d.getMessage().getLeft() : "";
            return msg.contains("non_existent_col") && !msg.startsWith(RmlValidationService.REVALIDATING_PREFIX);
        });

        // 2. Perform edit (didChange)
        String updatedText = text + "\n# Some comment\n";
        server.getTextDocumentService().didChange(new DidChangeTextDocumentParams(
                new VersionedTextDocumentIdentifier(uri, 2),
                List.of(new TextDocumentContentChangeEvent(updatedText))
        ));

        // Immediately check: dynamic diagnostic must be tagged with [revalidating...]
        List<Diagnostic> immediatelyAfterEdit = workspace.getValidationService().getLastPublishedDiagnostics(uri);
        assertThat(immediatelyAfterEdit).anyMatch(d -> {
            String msg = d.getMessage() != null && d.getMessage().isLeft() ? d.getMessage().getLeft() : "";
            return msg.contains("non_existent_col") && msg.startsWith(RmlValidationService.REVALIDATING_PREFIX);
        });

        // 3. Wait for debounce delay (150ms + margin)
        start = System.currentTimeMillis();
        boolean clearedRevalidating = false;
        while (System.currentTimeMillis() - start < 2000) {
            List<Diagnostic> latest = workspace.getValidationService().getLastPublishedDiagnostics(uri);
            boolean hasFresh = latest.stream().anyMatch(d -> {
                String msg = d.getMessage() != null && d.getMessage().isLeft() ? d.getMessage().getLeft() : "";
                return msg.contains("non_existent_col") && !msg.startsWith(RmlValidationService.REVALIDATING_PREFIX);
            });
            if (hasFresh) {
                clearedRevalidating = true;
                break;
            }
            Thread.sleep(30);
        }

        assertThat(clearedRevalidating).isTrue();
    }

    @Test
    public void testDidSaveRunsImmediatelyWithoutDebounce() throws Exception {
        String path = "src/test/resources/burp/validate/invalid-sql-col.ttl";
        String uri = toUri(path);
        String text = Files.readString(Path.of(path));

        // Set huge debounce delay to prove didSave bypasses debounce
        workspace.getValidationService().setDebounceDelayMs(10000L);

        TextDocumentItem item = new TextDocumentItem(uri, "turtle", 1, text);
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(item));

        // Call didSave
        server.getTextDocumentService().didSave(new DidSaveTextDocumentParams(
                new TextDocumentIdentifier(uri),
                text
        ));

        // Dynamic diagnostics must be published immediately
        List<Diagnostic> published = workspace.getValidationService().getLastPublishedDiagnostics(uri);
        assertThat(published).anyMatch(d -> {
            String msg = d.getMessage() != null && d.getMessage().isLeft() ? d.getMessage().getLeft() : "";
            return msg.contains("non_existent_col") && !msg.startsWith(RmlValidationService.REVALIDATING_PREFIX);
        });
    }

    @Test
    public void testDidCloseClearsDiagnostics() throws Exception {
        String path = "src/test/resources/burp/validate/invalid-sql-col.ttl";
        String uri = toUri(path);
        String text = Files.readString(Path.of(path));

        TextDocumentItem item = new TextDocumentItem(uri, "turtle", 1, text);
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(item));

        server.getTextDocumentService().didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)));

        // Published diagnostics for uri must be cleared
        assertThat(client.publishedDiagnostics).isNotEmpty();
        PublishDiagnosticsParams lastParams = client.publishedDiagnostics.getLast();
        assertThat(lastParams.getUri()).isEqualTo(uri);
        assertThat(lastParams.getDiagnostics()).isEmpty();
    }

    @Test
    public void testUnlocatedErrorEmitsToastAndInfoOnResolution() throws Exception {
        String uri = toUri("src/test/resources/burp/validate/missing-file.ttl");

        // didSave on non-existent file
        server.getTextDocumentService().didSave(new DidSaveTextDocumentParams(new TextDocumentIdentifier(uri)));

        // Check showMessages received error toast
        assertThat(client.showMessages).anyMatch(m ->
                m.getType() == MessageType.Error && m.getMessage().contains("Failed to validate mapping")
        );

        // Published diagnostics for this file must have null or empty ranges (no false squiggles)
        List<Diagnostic> diags = workspace.getValidationService().getLastPublishedDiagnostics(uri);
        assertThat(diags).isEmpty();

        // Now supply valid content and save
        String validTurtle = """
                PREFIX rml: <http://w3id.org/rml/>
                <#TM> a rml:TriplesMap ;
                    rml:logicalSource [
                        a rml:LogicalSource ;
                        rml:source [ a rml:Source ] ;
                        rml:referenceFormulation rml:JSONPath ;
                        rml:iterator "$"
                    ] ;
                    rml:subjectMap [
                        rml:template "http://example.com/test"
                    ] .
                """;
        server.getTextDocumentService().didSave(new DidSaveTextDocumentParams(
                new TextDocumentIdentifier(uri),
                validTurtle
        ));

        // Check that "Mapping loaded successfully." info toast was emitted
        assertThat(client.showMessages).anyMatch(m ->
                m.getType() == MessageType.Info && m.getMessage().equals("Mapping loaded successfully.")
        );
    }
}
