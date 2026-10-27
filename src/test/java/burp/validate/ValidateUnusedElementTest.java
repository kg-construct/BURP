package burp.validate;

import burp.dev.rml.RmlDevServerImpl;
import burp.dev.rml.service.validator.RmlDiagnostic;
import burp.dev.rml.service.validator.ValidateParams;
import burp.vocabularies.RER;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

public class ValidateUnusedElementTest {

    private String toUri(String path) {
        return Path.of(path).toUri().toString();
    }

    @Test
    public void testValidateUnusedRmlElements() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/validate/unused-element.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();
        assertThat(diagnostics).anyMatch(d ->
                d.severity() == DiagnosticSeverity.Warning &&
                        RER.UnusedRmlElement.getURI().equals(d.rerCodeUri()) &&
                d.message().contains("OrphanSubjectMap")
        );
    }

    @Test
    public void testValidateUnusedAnonymousGatherElement() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/validate_unused_gather/mapping.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();
        assertThat(diagnostics).anyMatch(d ->
                d.severity() == DiagnosticSeverity.Warning &&
                        RER.UnusedRmlElement.getURI().equals(d.rerCodeUri()) &&
                d.message().contains("Unused RML element") &&
                d.range().getStart().getLine() == 12
        );
    }

    @Test
    public void testValidateActiveLogicalSourceDoesNotFlagFilePathAsUnused() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/validate/invalid-param.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();
        assertThat(diagnostics).noneMatch(d ->
                RER.UnusedRmlElement.getURI().equals(d.rerCodeUri()) &&
                (d.message().contains("RelativePathSource") || d.message().contains("FilePath"))
        );
    }
}
