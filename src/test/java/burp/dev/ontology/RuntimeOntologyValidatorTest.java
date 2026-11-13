package burp.dev.ontology;

import burp.dev.rml.RmlDevServerImpl;
import burp.dev.rml.service.validator.RmlDiagnostic;
import burp.dev.rml.service.validator.ValidateParams;
import burp.vocabularies.RER;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@NullMarked
public class RuntimeOntologyValidatorTest {

    private static final String DIR = "src/test/resources/burp/ontology/";

    private RmlDevServerImpl server = new RmlDevServerImpl();

    @BeforeEach
    public void setUp() {
        server = new RmlDevServerImpl();
    }

    @AfterEach
    public void tearDown() {
        server.shutdown();
    }

    @Test
    public void testDynamicDomainMismatch() throws Exception {
        Path mappingFile = Path.of(DIR, "dynamic_domain_mismatch.ttl").toAbsolutePath();
        String uri = mappingFile.toUri().toString();

        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();

        assertThat(diagnostics)
                .filteredOn(d -> RER.OntologyDomainMismatch.getURI().equals(d.rerCodeUri()))
                .hasSize(1);
        assertThat(diagnostics.getFirst().message())
                .contains("ex:worksAt");
    }

    @Test
    public void testDynamicTermTypeMismatch() throws Exception {
        Path mappingFile = Path.of(DIR, "dynamic_term_type_mismatch.ttl").toAbsolutePath();
        String uri = mappingFile.toUri().toString();

        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();

        assertThat(diagnostics)
                .filteredOn(d -> RER.OntologyTermTypeMismatch.getURI().equals(d.rerCodeUri()))
                .hasSize(1);
        assertThat(diagnostics.getFirst().message())
                .contains("ex:treatedBy");
    }

    @Test
    public void testDynamicIllTypedLiteral() throws Exception {
        Path mappingFile = Path.of(DIR, "dynamic_ill_typed_literal.ttl").toAbsolutePath();
        String uri = mappingFile.toUri().toString();

        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();

        assertThat(diagnostics)
                .filteredOn(d -> RER.OntologyDatatypeMismatch.getURI().equals(d.rerCodeUri()))
                .hasSize(1);
        assertThat(diagnostics.getFirst().message())
                .contains("abc")
                .contains("xsd:integer");
    }
}
