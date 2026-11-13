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

public class ValidateSqlTest {

    private String toUri(String path) {
        return Path.of(path).toUri().toString();
    }

    @Test
    public void testValidateValidSqlTableMappingHasNoErrors() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/BURPIOTC-SQLITE/mapping.ttl");
        RmlLanguageServer server = new RmlDevServerImpl();
        List<RmlErrorDto> diagnostics = server.validate(new ValidateParams(uri)).get();

        assertThat(diagnostics).isNotNull();
        List<RmlErrorDto> errors = diagnostics.stream()
                .filter(d -> "ERROR".equals(d.severity()))
                .toList();
        assertThat(errors).isEmpty();

        List<RmlErrorDto> warnings = diagnostics.stream()
                .filter(d -> "WARNING".equals(d.severity()))
                .filter(d -> !RER.UnusedRmlElement.getURI().equals(d.errorTypeUri()))
                .toList();
        assertThat(warnings).isEmpty();
    }

    @Test
    public void testValidateInvalidSqlColumnReference() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/validate/invalid-sql-col.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlErrorDto> diagnostics = server.validate(new ValidateParams(uri)).get();
        assertThat(diagnostics).anyMatch(d ->
                "ERROR".equals(d.severity()) &&
                RER.ReferenceFormulationExecutionError.getURI().equals(d.errorTypeUri()) &&
                d.message().contains("non_existent_col")
        );
    }

    @Test
    public void testValidateInvalidSqlQuerySyntax() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/validate/bad-sql-query.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlErrorDto> diagnostics = server.validate(new ValidateParams(uri)).get();

        assertThat(diagnostics).anyMatch(d ->
                "ERROR".equals(d.severity()) &&
                RER.IteratorSyntaxError.getURI().equals(d.errorTypeUri())
        );

        assertThat(diagnostics).noneMatch(d ->
                RER.ReferenceFormulationExecutionError.getURI().equals(d.errorTypeUri())
        );
    }

    @Test
    public void testValidateUnreachableDatabase() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/validate/bad-db-dsn.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlErrorDto> diagnostics = server.validate(new ValidateParams(uri)).get();

        assertThat(diagnostics).anyMatch(d ->
                "ERROR".equals(d.severity()) &&
                RER.SourceAccessError.getURI().equals(d.errorTypeUri())
        );

        assertThat(diagnostics).noneMatch(d ->
                RER.ReferenceFormulationExecutionError.getURI().equals(d.errorTypeUri())
        );
    }

    @Test
    public void testValidateCaseInsensitiveSqlColumnMatching() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/validate/case-insensitive-sql-col.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlErrorDto> diagnostics = server.validate(new ValidateParams(uri)).get();

        assertThat(diagnostics).noneMatch(d ->
                RER.ReferenceFormulationExecutionError.getURI().equals(d.errorTypeUri())
        );
    }

    @Test
    public void testClearCacheInvalidatesSqlSchemas() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/BURPIOTC-SQLITE/mapping.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        server.validate(new ValidateParams(uri)).get();

        assertThat(server.getWorkspace().getCache().getSqlSchemaCacheSize()).isGreaterThan(0);
        server.clearCache().get();
        assertThat(server.getWorkspace().getCache().getSqlSchemaCacheSize()).isEqualTo(0);
    }
}
