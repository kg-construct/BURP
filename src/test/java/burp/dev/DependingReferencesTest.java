package burp.dev;

import burp.dev.rml.RmlDevServerImpl;
import burp.dev.rml.RmlLanguageServer;
import burp.dev.rml.service.selector.CursorSelector;
import burp.dev.rml.service.plan.ReferenceDependency;
import burp.dev.rml.service.plan.ReferencesDependenciesParams;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

public class DependingReferencesTest {

    @Test
    public void testReferencesDependenciesAlice() throws ExecutionException, InterruptedException {
        ReferencesDependenciesParams params = new ReferencesDependenciesParams(
                new CursorSelector("src/test/resources/burp/eval_alice/mapping.ttl", new Position(0, 0))
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        List<ReferenceDependency> deps = server.referencesDependencies(params).get();

        assertThat(deps)
                .isNotNull()
                .isNotEmpty();

        assertThat(deps)
                .extracting(ReferenceDependency::referenceId)
                .containsExactlyInAnyOrder("$.name", "$.age");

        assertThat(deps).allSatisfy(dep -> {
            assertThat(dep.referenceId())
                    .doesNotStartWithIgnoringCase("rawreference")
                    .doesNotStartWithIgnoringCase("referencesegment")
                    .doesNotStartWithIgnoringCase("template");
        });
    }

    @Test
    public void testReferencesDependenciesFromRmlCoreTestCase() throws ExecutionException, InterruptedException {
        ReferencesDependenciesParams params = new ReferencesDependenciesParams(
                new CursorSelector("rml-modules/rml-core/test-cases/RMLTC0002a-JSON/mapping.ttl", new Position(0, 0))
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        List<ReferenceDependency> deps = server.referencesDependencies(params).get();

        assertThat(deps)
                .isNotNull()
                .isNotEmpty();

        assertThat(deps)
                .extracting(ReferenceDependency::referenceId)
                .containsExactlyInAnyOrder("$.ID", "$.Name");

        assertThat(deps).allSatisfy(dep -> {
            assertThat(dep.placeholders()).isNotEmpty().first().isEqualTo(dep.referenceId());
        });
    }

    @Test
    public void testReferencesDependenciesFromCsvFnmlConcat() throws ExecutionException, InterruptedException {
        ReferencesDependenciesParams params = new ReferencesDependenciesParams(
                new CursorSelector("src/test/resources/burp/BURPFNMLTC-CSV-Concat/mapping.ttl", new Position(0, 0))
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        List<ReferenceDependency> deps = server.referencesDependencies(params).get();

        assertThat(deps)
                .isNotNull()
                .isNotEmpty();

        assertThat(deps)
                .extracting(ReferenceDependency::referenceId)
                .containsExactlyInAnyOrder("Id", "Name", "Class");
    }
}
