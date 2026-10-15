package burp.dev;

import burp.dev.rml.RmlDevServerImpl;
import burp.dev.rml.RmlLanguageServer;
import burp.dev.rml.service.eval.AllDataContext;
import burp.dev.rml.service.eval.EvaluateParams;
import burp.dev.rml.service.eval.EvaluateResult;
import burp.dev.rml.service.selector.CursorSelector;
import burp.dev.rml.service.execute.ExecuteParams;
import burp.dev.rml.service.execute.ExecuteResult;
import com.google.gson.GsonBuilder;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

public class ExecuteTest {

    @Test
    public void testDevServerExecute() throws ExecutionException, InterruptedException {
        RmlLanguageServer server = new RmlDevServerImpl();
        ExecuteParams params = new ExecuteParams(
                new CursorSelector("src/test/resources/burp/eval_alice/mapping.ttl", new Position(6, 1)),
                "test-exec-1",
                null
        );

        ExecuteResult result = server.execute(params).get();

        assertThat(result).isNotNull();
        assertThat(result.targets()).isNotEmpty();
        assertThat(result.targets()).containsKey("default");
        assertThat(result.targets().get("default").statementCount()).isGreaterThan(0);
        assertThat(result.targets().get("default").statements().stream().map(Object::toString).toList().toString()).contains("http://example.org/person/Alice");

        // Test clearCache
        server.clearCache().get();

        // Verify Gson serialization doesn't overflow
        var gsonBuilder = new GsonBuilder();
        DevServer.configureGson(gsonBuilder);
        var gson = gsonBuilder.create();
        String json = gson.toJson(result);
        assertThat(json).isNotEmpty();
    }

    @Test
    public void testDevServerExecuteWithLogicalTargetAndGson() throws Exception {
        File rmlttc = new File("/home/jakub/Documents/Dev/BURP/rml-modules/rml-io/test-cases/RMLTTC0001a/mapping.ttl");
        if (!rmlttc.exists()) {
            return;
        }

        RmlLanguageServer server = new RmlDevServerImpl();
        ExecuteParams params = new ExecuteParams(
                new CursorSelector(rmlttc.getAbsolutePath(), new Position(6, 1)),
                "test-exec-logical-target",
                null
        );

        ExecuteResult result = server.execute(params).get();
        assertThat(result).isNotNull();
        assertThat(result.targets()).isNotEmpty();

        // Check logicalTarget metadata is populated and not raw Jena Resource
        boolean foundCustomTarget = false;
        for (var entry : result.targets().entrySet()) {
            if (entry.getValue().logicalTarget() != null) {
                foundCustomTarget = true;
                assertThat(entry.getValue().logicalTarget().targetPath()).isNotNull();
            }
        }
        assertThat(foundCustomTarget).isTrue();

        // Crucial test: Gson serialization MUST succeed without StackOverflowError
        GsonBuilder builder = new GsonBuilder();
        DevServer.configureGson(builder);
        com.google.gson.Gson gson = builder.create();
        String json = gson.toJson(result);
        assertThat(json).isNotEmpty();
        assertThat(json).contains("targetPath");
    }

    @Test
    public void testDevServerEvaluateWithLogicalTargetAndGson() throws Exception {
        File rmlttc = new File("/home/jakub/Documents/Dev/BURP/rml-modules/rml-io/test-cases/RMLTTC0001a/mapping.ttl");
        if (!rmlttc.exists()) {
            return;
        }

        RmlLanguageServer server = new RmlDevServerImpl();
        EvaluateParams params = new EvaluateParams(
                new CursorSelector(rmlttc.getAbsolutePath(), new Position(6, 1)),
                new AllDataContext()
        );

        java.util.List<EvaluateResult> results = server.evaluate(params).get();
        assertThat(results).isNotEmpty();

        GsonBuilder builder = new GsonBuilder();
        DevServer.configureGson(builder);
        com.google.gson.Gson gson = builder.create();

        String json = gson.toJson(results);
        assertThat(json).isNotEmpty();
        assertThat(json).doesNotContain("enhGraph");
        assertThat(json).contains("./dump1.nq");
        assertThat(json).contains("CurrentWorkingDirectory");
    }

    @Test
    public void testDevServerExecuteRMLTTC0001d() throws Exception {
        File rmlttc = new File("/home/jakub/Documents/Dev/BURP/rml-modules/rml-io/test-cases/RMLTTC0001d/mapping.ttl");
        if (!rmlttc.exists()) return;

        RmlLanguageServer server = new RmlDevServerImpl();
        ExecuteParams params = new ExecuteParams(
                new CursorSelector(rmlttc.getAbsolutePath(), new Position(6, 1)),
                "test-exec-rmlttc0001d",
                null
        );

        ExecuteResult result = server.execute(params).get();
        assertThat(result.targets()).containsKey("./dump1.nq");
        assertThat(result.targets().get("./dump1.nq").statements().stream().map(Object::toString).toList().toString()).contains("<http://example.org/PeopleGraph>");
    }

    @Test
    public void testDevServerExecuteRMLTTC0002k() throws Exception {
        File rmlttc = new File("/home/jakub/Documents/Dev/BURP/rml-modules/rml-io/test-cases/RMLTTC0002k/mapping.ttl");
        if (!rmlttc.exists()) return;

        RmlLanguageServer server = new RmlDevServerImpl();
        ExecuteParams params = new ExecuteParams(
                new CursorSelector(rmlttc.getAbsolutePath(), new Position(6, 1)),
                "test-exec-rmlttc0002k",
                null
        );

        ExecuteResult result = server.execute(params).get();
        assertThat(result.targets()).containsKeys("default", "./dump1.nq", "./dump2.nq");
        assertThat(result.targets().get("./dump1.nq").statements().stream().map(Object::toString).toList().toString()).contains("<http://example.org/PeopleGraph>");
        assertThat(result.targets().get("./dump2.nq").statements().stream().map(Object::toString).toList().toString()).contains("<http://example.org/PeopleGraph>");
        assertThat(result.targets().get("default").statements().stream().map(Object::toString).toList().toString()).doesNotContain("<http://example.org/PeopleGraph>");
    }

    @Test
    public void testDevServerExecuteWithBaseIriFromParams() throws Exception {
        File mappingFile = new File("rml-modules/rml-cc/test-cases/RMLTC-CC-0010-Lista/mapping.ttl");
        if (!mappingFile.exists()) return;

        RmlLanguageServer server = new RmlDevServerImpl();
        ExecuteParams params = new ExecuteParams(
                new CursorSelector(mappingFile.getAbsolutePath(), new Position(5, 1)),
                "test-base-iri-params",
                "http://example.com/base/"
        );

        ExecuteResult result = server.execute(params).get();
        assertThat(result.targets()).containsKey("default");
        assertThat(result.targets().get("default").statements().stream().map(Object::toString).toList().toString()).contains("<http://example.com/base/e/a>");
    }

    @Test
    public void testDevServerExecuteWithBaseIriFromWorkspaceConfiguration() throws Exception {
        File mappingFile = new File("rml-modules/rml-cc/test-cases/RMLTC-CC-0010-Lista/mapping.ttl");
        if (!mappingFile.exists()) return;

        RmlDevServerImpl server = new RmlDevServerImpl();
        server.getWorkspaceService().didChangeConfiguration(new org.eclipse.lsp4j.DidChangeConfigurationParams(
                java.util.Map.of("rml", java.util.Map.of("baseIRI", "http://example.com/base/"))
        ));

        ExecuteParams params = new ExecuteParams(
                new CursorSelector(mappingFile.getAbsolutePath(), new Position(5, 1)),
                "test-base-iri-config",
                null
        );

        ExecuteResult result = server.execute(params).get();
        assertThat(result.targets()).containsKey("default");
        assertThat(result.targets().get("default").statements().stream().map(Object::toString).toList().toString()).contains("<http://example.com/base/e/a>");
    }

    @Test
    public void testDevServerExecuteWithConfigurationPullModel() throws Exception {
        java.io.File mappingFile = new java.io.File("rml-modules/rml-cc/test-cases/RMLTC-CC-0010-Lista/mapping.ttl");
        if (!mappingFile.exists()) return;

        RmlDevServerImpl server = new RmlDevServerImpl();
        org.eclipse.lsp4j.services.LanguageClient mockClient = (org.eclipse.lsp4j.services.LanguageClient) java.lang.reflect.Proxy.newProxyInstance(
                org.eclipse.lsp4j.services.LanguageClient.class.getClassLoader(),
                new Class<?>[]{org.eclipse.lsp4j.services.LanguageClient.class},
                (proxy, method, args) -> {
                    if ("configuration".equals(method.getName())) {
                        return CompletableFuture.completedFuture(java.util.List.of(
                                java.util.Map.of("baseIRI", "http://example.com/base/")
                        ));
                    }
                    return null;
                }
        );

        server.connect(mockClient);

        ExecuteParams params = new ExecuteParams(
                new CursorSelector(mappingFile.getAbsolutePath(), new Position(5, 1)),
                "test-base-iri-pull-model",
                null
        );

        ExecuteResult result = server.execute(params).get();
        assertThat(result.targets()).containsKey("default");
        assertThat(result.targets().get("default").statements().stream().map(Object::toString).toList().toString()).contains("<http://example.com/base/e/a>");
    }
}
