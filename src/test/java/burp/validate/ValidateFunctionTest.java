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

public class ValidateFunctionTest {

    private String toUri(String path) {
        return Path.of(path).toUri().toString();
    }

    @Test
    public void testValidateDuplicateParameterBinding() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/validate/dup-param.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();
        assertThat(diagnostics).anyMatch(d ->
                d.severity() == DiagnosticSeverity.Error &&
                RER.DuplicateParameter.getURI().equals(d.rerCodeUri()) &&
                d.message().contains("Duplicate parameter binding")
        );
    }

    @Test
    public void testValidateInvalidFunctionParameter() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/validate/invalid-param.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();
        assertThat(diagnostics).anyMatch(d ->
                d.severity() == DiagnosticSeverity.Warning &&
                RER.InvalidParameter.getURI().equals(d.rerCodeUri()) &&
                d.message().contains("nonExistentParam")
        );
    }

    @Test
    public void testValidateMissingRequiredFunctionParameter() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/validate/missing-param.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();
        assertThat(diagnostics).anyMatch(d ->
                d.severity() == DiagnosticSeverity.Error &&
                RER.MissingParameter.getURI().equals(d.rerCodeUri()) &&
                (d.message().contains("from") || d.message().contains("p_int_i_from"))
        );
    }

    @Test
    public void testValidateUnknownFunction() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/validate/unknown-func.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();
        assertThat(diagnostics).anyMatch(d ->
                d.severity() == DiagnosticSeverity.Warning &&
                RER.UnknownFunction.getURI().equals(d.rerCodeUri()) &&
                d.message().contains("nonExistentFunction")
        );
    }

    @Test
    public void testValidateCustomOntologyFunctions() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/validate/custom-ontologies.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();
        assertThat(diagnostics).noneMatch(d ->
                RER.UnknownFunction.getURI().equals(d.rerCodeUri()) ||
                RER.InvalidParameter.getURI().equals(d.rerCodeUri()) ||
                RER.MissingParameter.getURI().equals(d.rerCodeUri())
        );
    }

    @Test
    public void testValidatePredicateParameterIriWarning() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/validate/raw-param.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();
        assertThat(diagnostics).anyMatch(d ->
                d.severity() == DiagnosticSeverity.Warning &&
                RER.UnknownFunctionParameterIRI.getURI().equals(d.rerCodeUri()) &&
                d.message().contains("str")
        );
    }

    @Test
    public void testValidatePredicateParameterIriWarningWithFilePrefixes() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/validate/raw-param-prefix.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();
        assertThat(diagnostics).anyMatch(d ->
                d.severity() == DiagnosticSeverity.Warning &&
                RER.UnknownFunctionParameterIRI.getURI().equals(d.rerCodeUri()) &&
                d.message().equals("Parameter grel:valueParameter is a predicate IRI; consider using its parameter definition IRI grel:valueParam instead.")
        );
    }

    @Test
    public void testValidateParameterEntityIriProducesNoWarning() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/validate/custom-ontologies.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();
        assertThat(diagnostics).noneMatch(d ->
                RER.UnknownFunctionParameterIRI.getURI().equals(d.rerCodeUri()) ||
                RER.UnknownFunctionReturnIRI.getURI().equals(d.rerCodeUri())
        );
    }
}
