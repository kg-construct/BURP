package burp.validate;

import burp.dev.rml.RmlDevServerImpl;
import burp.dev.rml.RmlLanguageServer;
import burp.dev.rml.service.validator.RmlDiagnostic;
import burp.dev.rml.service.validator.ValidateParams;
import burp.vocabularies.RER;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class ValidateReferenceTest {

    private String toUri(String path) {
        return Path.of(path).toUri().toString();
    }

    @Test
    public void testValidateValidMappingHasNoErrors() throws ExecutionException, InterruptedException {
        ValidateParams params = new ValidateParams(toUri("src/test/resources/burp/BURPFNMLTC-CSV-Concat/mapping.ttl"));
        RmlLanguageServer server = new RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(params).get();

        assertThat(diagnostics).isNotNull();
        List<RmlDiagnostic> errors = diagnostics.stream()
                .filter(d -> d.severity() == DiagnosticSeverity.Error)
                .toList();
        assertThat(errors).isEmpty();
    }

    @Test
    public void testValidateInvalidTemplateSyntax() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/validate/invalid-template.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();
        assertThat(diagnostics).anyMatch(d ->
                d.severity() == DiagnosticSeverity.Error &&
                        RER.ReferenceFormulationSyntaxError.getURI().equals(d.rerCodeUri()) &&
                        d.message().contains("Empty reference expression '{}'")
        );
    }

    @Test
    public void testValidateInvalidJsonPathSyntax() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/validate/invalid-jsonpath.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();
        assertThat(diagnostics).anyMatch(d ->
                d.severity() == DiagnosticSeverity.Error &&
                        RER.ReferenceFormulationSyntaxError.getURI().equals(d.rerCodeUri())
        );
    }

    @Test
    public void testValidateInvalidJsonPathWithEscapedBracesInsideTemplate() throws Exception {
        String uri = toUri("src/test/resources/burp/validate/invalid-jsonpath-escaped-braces.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();

        List<RmlDiagnostic> errors = diagnostics.stream()
                .filter(d -> d.severity() == DiagnosticSeverity.Error &&
                        RER.ReferenceFormulationSyntaxError.getURI().equals(d.rerCodeUri()))
                .toList();

        assertThat(errors).isNotEmpty();
        assertThat(errors).allSatisfy(error -> {
            assertThat(error.range().getStart().getLine()).isEqualTo(10);
            assertThat(error.range().getStart().getCharacter()).isGreaterThanOrEqualTo(60);
            assertThat(error.range().getEnd().getLine()).isEqualTo(10);
            assertThat(error.range().getEnd().getCharacter()).isEqualTo(63);
        });
        assertThat(errors).anyMatch(error -> error.range().getStart().getCharacter() == 60);
    }

    @Test
    public void testValidateJoinConditionShortcutErrorPosition() throws Exception {
        String uri = Path.of("src/test/resources/validate/invalid_join_condition_shortcut/mapping.ttl").toAbsolutePath().toUri().toString();
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();

        assertThat(diagnostics)
                .filteredOn(d -> RER.ReferenceFormulationSyntaxError.getURI().equals(d.rerCodeUri()))
                .extracting(d -> tuple(d.range().getStart().getLine(), d.range().getStart().getCharacter()))
                .containsExactlyInAnyOrder(
                        tuple(21, 25),
                        tuple(22, 26)
                );
    }

    @Test
    public void testValidateLogicalTargetNoUnusedWarnings() throws Exception {
        String uri = Path.of("rml-modules/rml-io/test-cases/RMLTTC0001a/mapping.ttl").toAbsolutePath().toUri().toString();
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();

        assertThat(diagnostics)
                .filteredOn(d -> d.severity() == DiagnosticSeverity.Warning &&
                        RER.UnusedRmlElement.getURI().equals(d.rerCodeUri()) &&
                        d.message().contains("TargetDump1"))
                .isEmpty();
    }

    @Test
    public void testValidateLogicalViewAndIterableFieldsNoWarnings() throws Exception {
        RmlDevServerImpl server = new RmlDevServerImpl();
        Path elsdvleeDir = Path.of("src/test/resources/burp/ELSDVLEE-20").toAbsolutePath();
        server.getWorkspace().setCurrentWorkingDirectory(elsdvleeDir);

        String elsdvleeUri = elsdvleeDir.resolve("mapping.ttl").toUri().toString();
        List<RmlDiagnostic> elsdvleeDiagnostics = server.validate(new ValidateParams(elsdvleeUri)).get();
        assertThat(elsdvleeDiagnostics).isEmpty();

        String evalLvUri = Path.of("src/test/resources/burp/eval_logicalview/mapping.ttl").toAbsolutePath().toUri().toString();
        List<RmlDiagnostic> evalLvDiagnostics = server.validate(new ValidateParams(evalLvUri)).get();
        assertThat(evalLvDiagnostics).isEmpty();
    }
}
