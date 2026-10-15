package burp.validate;

import burp.dev.rml.RmlDevServerImpl;
import burp.dev.rml.service.validator.RmlDiagnostic;
import burp.dev.rml.service.validator.ValidateParams;
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
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();

        assertThat(diagnostics)
                .filteredOn(d -> d.severity() == DiagnosticSeverity.Error && "RML_LOAD_ERROR".equals(d.rerCodeUri()))
                .satisfiesExactly(d -> {
                    assertThat(d.message()).isEqualTo("Failed to validate mapping: RelativePathSource specified root http://w3id.org/rml/MappingDircetory is not supported.");
                    assertThat(d.range()).isNotNull();
                    assertThat(tuple(
                            d.range().getStart().getLine(),
                            d.range().getStart().getCharacter(),
                            d.range().getEnd().getLine(),
                            d.range().getEnd().getCharacter()
                    )).isEqualTo(tuple(7, 12, 7, 41));
                });
    }

    @Test
    public void testValidateMissingFileHasNullRange() throws Exception {
        String uri = toUri("src/test/resources/burp/validate/non-existent-mapping-file.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();

        assertThat(diagnostics)
                .filteredOn(d -> d.severity() == DiagnosticSeverity.Error && "RML_LOAD_ERROR".equals(d.rerCodeUri()))
                .satisfiesExactly(d -> assertThat(d.range()).isNull());
    }
}
