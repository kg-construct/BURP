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

public class ValidateSqlTest {

    private String toUri(String path) {
        return Path.of(path).toUri().toString();
    }

    @Test
    public void testValidateValidSqlTableMappingHasNoErrors() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/BURPIOTC-SQLITE/mapping.ttl");
        RmlLanguageServer server = new RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();

        assertThat(diagnostics).isNotNull();
        List<RmlDiagnostic> errors = diagnostics.stream()
                .filter(d -> d.severity() == DiagnosticSeverity.Error)
                .toList();
        assertThat(errors).isEmpty();

        List<RmlDiagnostic> warnings = diagnostics.stream()
                .filter(d -> d.severity() == DiagnosticSeverity.Warning)
                .filter(d -> !RER.UnusedRmlElement.getURI().equals(d.rerCodeUri()))
                .toList();
        assertThat(warnings).isEmpty();
    }

    @Test
    public void testValidateInvalidSqlColumnReference() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/validate/invalid-sql-col.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();
        assertThat(diagnostics).anyMatch(d ->
                d.severity() == DiagnosticSeverity.Warning &&
                RER.ReferenceFormulationExecutionError.getURI().equals(d.rerCodeUri()) &&
                d.message().contains("non_existent_col")
        );
    }

    @Test
    public void testValidateInvalidSqlQuerySyntax() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/validate/bad-sql-query.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();

        assertThat(diagnostics).anyMatch(d ->
                d.severity() == DiagnosticSeverity.Error &&
                RER.ReferenceFormulationSyntaxError.getURI().equals(d.rerCodeUri())
        );

        assertThat(diagnostics).noneMatch(d ->
                RER.ReferenceFormulationExecutionError.getURI().equals(d.rerCodeUri())
        );
    }

    @Test
    public void testValidateUnreachableDatabase() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/validate/bad-db-dsn.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();

        assertThat(diagnostics).anyMatch(d ->
                d.severity() == DiagnosticSeverity.Information &&
                RER.SourceAccessError.getURI().equals(d.rerCodeUri())
        );

        assertThat(diagnostics).noneMatch(d ->
                RER.ReferenceFormulationExecutionError.getURI().equals(d.rerCodeUri())
        );
    }

    @Test
    public void testValidateCaseInsensitiveSqlColumnMatching() throws ExecutionException, InterruptedException {
        String uri = toUri("src/test/resources/burp/validate/case-insensitive-sql-col.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();

        assertThat(diagnostics).noneMatch(d ->
                RER.ReferenceFormulationExecutionError.getURI().equals(d.rerCodeUri())
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
