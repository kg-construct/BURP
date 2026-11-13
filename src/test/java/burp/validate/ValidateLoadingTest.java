package burp.validate;

import burp.dev.rml.RmlDevServerImpl;
import burp.dev.rml.service.validator.RmlErrorDto;
import burp.dev.rml.service.validator.ValidateParams;
import burp.vocabularies.RER;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@NullMarked
public class ValidateLoadingTest {

    private String toUri(String path) {
        return Path.of(path).toUri().toString();
    }

    @Test
    public void testValidateUnsupportedRelativePathSourceRootErrorPosition() throws Exception {
        String uri = toUri("src/test/resources/burp/validate/invalid-root.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlErrorDto> diagnostics = server.validate(new ValidateParams(uri)).get();

        assertThat(diagnostics)
                .filteredOn(d -> "ERROR".equals(d.severity()) && RER.UnsupportedMapping.getURI().equals(d.errorTypeUri()))
                .satisfiesExactly(d -> {
                    assertThat(d.message()).contains("http://w3id.org/rml/MappingDircetory");
                    assertThat(d.ranges()).isNotEmpty();
                    assertThat(tuple(
                            d.ranges().getFirst().start().line(),
                            d.ranges().getFirst().start().column(),
                            d.ranges().getLast().end().line(),
                            d.ranges().getLast().end().column()
                    )).isEqualTo(tuple(7, 12, 7, 41));
                });
    }

    @Test
    public void testValidateMissingFileHasNullRange() throws Exception {
        String uri = toUri("src/test/resources/burp/validate/non-existent-mapping-file.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlErrorDto> diagnostics = server.validate(new ValidateParams(uri)).get();

        assertThat(diagnostics)
                .filteredOn(d -> "ERROR".equals(d.severity()) && RER.CannotReadMappingDocument.getURI().equals(d.errorTypeUri()))
                .satisfiesExactly(d -> assertThat(d.ranges()).isEmpty());
    }

    @Test
    public void testValidateSyntaxErrorPosition() throws Exception {
        String uri = toUri("src/test/resources/burp/nearly_valid/incomplete_class.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlErrorDto> diagnostics = server.validate(new ValidateParams(uri)).get();

        List<RmlErrorDto> syntaxErrors = diagnostics.stream()
                .filter(d -> "ERROR".equals(d.severity()) && d.message().contains("mismatched input ']'"))
                .toList();

        assertThat(syntaxErrors).hasSize(1);
        RmlErrorDto synErr = syntaxErrors.getFirst();
        assertThat(synErr.ranges()).isNotEmpty();
        // Offending token ']' is on line 11 (0-based, line 12 1-based), column 2
        assertThat(synErr.ranges().getFirst().start().line()).isEqualTo(11);
        assertThat(synErr.ranges().getFirst().start().column()).isEqualTo(2);
    }
}
