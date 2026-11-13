package burp.validate;

import burp.dev.rml.RmlDevServerImpl;
import burp.dev.rml.RmlLanguageServer;
import burp.dev.rml.service.validator.RmlErrorDto;
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
        List<RmlErrorDto> diagnostics = server.validate(params).get();

        assertThat(diagnostics).isNotNull();
        List<RmlErrorDto> errors = diagnostics.stream()
                .filter(d -> "ERROR".equals(d.severity()))
                .toList();
        assertThat(errors).isEmpty();
    }

    @Test
    public void testValidateInvalidTemplateSyntax() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/validate/invalid-template.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlErrorDto> diagnostics = server.validate(new ValidateParams(uri)).get();
        assertThat(diagnostics).anyMatch(d ->
                "ERROR".equals(d.severity()) &&
                        RER.TemplateSyntaxError.getURI().equals(d.errorTypeUri()) &&
                        d.message().contains("Empty reference expression '{}'")
        );
    }

    @Test
    public void testValidateInvalidJsonPathSyntax() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/validate/invalid-jsonpath.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlErrorDto> diagnostics = server.validate(new ValidateParams(uri)).get();
        assertThat(diagnostics).anyMatch(d ->
                "ERROR".equals(d.severity()) &&
                        RER.ReferenceFormulationSyntaxError.getURI().equals(d.errorTypeUri())
        );
    }

    @Test
    public void testValidateInvalidJsonPathWithEscapedBracesInsideTemplate() throws Exception {
        String uri = toUri("src/test/resources/burp/validate/invalid-jsonpath-escaped-braces.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlErrorDto> diagnostics = server.validate(new ValidateParams(uri)).get();

        List<RmlErrorDto> errors = diagnostics.stream()
                .filter(d -> "ERROR".equals(d.severity()) &&
                        RER.ReferenceFormulationSyntaxError.getURI().equals(d.errorTypeUri()))
                .toList();

        assertThat(errors).isNotEmpty();
        assertThat(errors).allSatisfy(error -> {
            assertThat(error.ranges().getFirst().start().line()).isEqualTo(10);
            assertThat(error.ranges().getFirst().start().column()).isGreaterThanOrEqualTo(60);
            assertThat(error.ranges().getFirst().end().line()).isEqualTo(10);
            assertThat(error.ranges().getFirst().end().column()).isEqualTo(63);
        });
        assertThat(errors).anyMatch(error -> error.ranges().getFirst().start().column() == 60);
    }

    @Test
    public void testValidateJoinConditionShortcutErrorPosition() throws Exception {
        String uri = Path.of("src/test/resources/validate/invalid_join_condition_shortcut/mapping.ttl").toAbsolutePath().toUri().toString();
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlErrorDto> diagnostics = server.validate(new ValidateParams(uri)).get();

        assertThat(diagnostics)
                .filteredOn(d -> RER.ReferenceFormulationSyntaxError.getURI().equals(d.errorTypeUri()))
                .extracting(d -> tuple(d.ranges().getFirst().start().line(), d.ranges().getFirst().start().column()))
                .containsExactlyInAnyOrder(
                        tuple(21, 25),
                        tuple(22, 26)
                );
    }

    @Test
    public void testValidateLogicalTargetNoUnusedWarnings() throws Exception {
        String uri = Path.of("rml-modules/rml-io/test-cases/RMLTTC0001a/mapping.ttl").toAbsolutePath().toUri().toString();
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlErrorDto> diagnostics = server.validate(new ValidateParams(uri)).get();

        assertThat(diagnostics)
                .filteredOn(d -> "WARNING".equals(d.severity()) &&
                        RER.UnusedRmlElement.getURI().equals(d.errorTypeUri()) &&
                        d.message().contains("TargetDump1"))
                .isEmpty();
    }

    @Test
    public void testValidateLogicalViewAndIterableFieldsNoWarnings() throws Exception {
        RmlDevServerImpl server = new RmlDevServerImpl();
        Path elsdvleeDir = Path.of("src/test/resources/burp/ELSDVLEE-20").toAbsolutePath();
        server.getWorkspace().setCurrentWorkingDirectory(elsdvleeDir);

        String elsdvleeUri = elsdvleeDir.resolve("mapping.ttl").toUri().toString();
        List<RmlErrorDto> elsdvleeDiagnostics = server.validate(new ValidateParams(elsdvleeUri)).get();
        assertThat(elsdvleeDiagnostics).isEmpty();

        String evalLvUri = Path.of("src/test/resources/burp/eval_logicalview/mapping.ttl").toAbsolutePath().toUri().toString();
        List<RmlErrorDto> evalLvDiagnostics = server.validate(new ValidateParams(evalLvUri)).get();
        assertThat(evalLvDiagnostics).isEmpty();
    }
}
