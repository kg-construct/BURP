package burp.validate;

import burp.dev.rml.service.validator.RmlDiagnostic;
import burp.dev.rml.service.validator.ValidateParams;
import burp.parse.RmlLoader;
import burp.parse.ValidationMode;
import burp.reporting.*;
import burp.vocabularies.RML;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests verifying SHACL validation of RML mappings,
 * TopBraid error reporting, sh:or branch unpacking, PTR statement provenance extraction,
 * and snippet rendering with caret (^^^) underlines using AssertJ.
 */
public class ShaclValidationTest {

    private final Path testResourcesDir = Paths.get("src/test/resources/validate").toAbsolutePath().normalize();

    @Test
    public void testMissingLogicalSourceReportsErrorAndOrigin() throws Exception {
        Path mappingPath = testResourcesDir.resolve("missing_logical_source/mapping.ttl");
        assertThat(mappingPath.toFile()).exists();

        RmlExecutionReport report = new RmlExecutionReport();
        RmlLoader loader = new RmlLoader(mappingPath)
                .withReport(report)
                .withValidationMode(ValidationMode.LENIENT);

        loader.loadMapping();

        assertThat(report.getErrors())
                .as("Should report errors for missing logical source")
                .isNotEmpty();

        RmlError error = report.getErrors().getFirst();
        Origin origin = error.origin();
        assertThat(origin)
                .as("Error should have an Origin")
                .isNotNull();
        assertThat(origin.file())
                .as("Origin should have mapping file path")
                .isEqualTo(mappingPath);

        // Assert provenance pointer (PTR) attaches to the TriplesMap subject
        assertThat(origin.sourceStatements())
                .as("Origin should have source statements")
                .isNotNull()
                .hasAtLeastOneElementOfType(StatementParts.class)
                .filteredOn(StatementParts.class::isInstance)
                .map(StatementParts.class::cast)
                .anySatisfy(sp -> {
                    assertThat(sp.subject())
                            .as("StatementParts should mark subject")
                            .isTrue();
                    assertThat(sp.stmt().getSubject().getURI())
                            .as("Subject URI should match TriplesMap")
                            .isEqualTo("http://example.com/InvalidTriplesMap");
                });

        // Verify snippet rendering with caret underline
        String textReport = PlainTextReportGenerator.generateTextReport(report, false);
        assertThat(textReport)
                .as("Report should display caret underlines when colors are disabled")
                .contains("^^^")
                .contains("InvalidTriplesMap");
    }

    @Test
    public void testMissingSubjectMapReportsError() throws Exception {
        Path mappingPath = testResourcesDir.resolve("missing_subject_map/mapping.ttl");
        assertThat(mappingPath.toFile()).exists();

        RmlExecutionReport report = new RmlExecutionReport();
        RmlLoader loader = new RmlLoader(mappingPath)
                .withReport(report)
                .withValidationMode(ValidationMode.LENIENT);

        loader.loadMapping();

        assertThat(report.getErrors())
                .as("Should report error for missing subject map")
                .isNotEmpty();

        String fullReportText = PlainTextReportGenerator.generateTextReport(report, false);

        assertThat(report.getErrors())
                .extracting(RmlError::message)
                .anySatisfy(msg ->
                        assertThat(msg)
                                .as("Report should mention missing subject or subjectMap:\n" + fullReportText)
                                .contains(RML.subjectMap.getLocalName())
                                .contains(RML.subject.getLocalName())
                );
    }

    @Test
    public void testInvalidTermTypeReportsErrorAndSnippet() throws Exception {
        Path mappingPath = testResourcesDir.resolve("invalid_term_type/mapping.ttl");
        assertThat(mappingPath.toFile()).exists();

        RmlExecutionReport report = new RmlExecutionReport();
        RmlLoader loader = new RmlLoader(mappingPath)
                .withReport(report)
                .withValidationMode(ValidationMode.LENIENT);

        loader.loadMapping();

        assertThat(report.getErrors())
                .as("Should report error for invalid termType Literal on SubjectMap")
                .isNotEmpty();

        // Verify that PTR provenance points to the statement with rml:termType
        assertThat(report.getErrors())
                .extracting(RmlError::origin)
                .filteredOn(java.util.Objects::nonNull)
                .flatExtracting(Origin::sourceStatements)
                .filteredOn(java.util.Objects::nonNull)
                .filteredOn(StatementParts.class::isInstance)
                .map(StatementParts.class::cast)
                .as("Provenance should pinpoint the rml:termType statement")
                .anySatisfy(sp ->
                        assertThat(sp.stmt().getPredicate())
                                .isEqualTo(RML.termType)
                );

        // Verify underline in plain text report
        String textReport = PlainTextReportGenerator.generateTextReport(report, false);
        assertThat(textReport)
                .as("Report should include the offending source code and caret underline")
                .contains("rml:termType rml:Literal")
                .contains("^^^");
    }

    @Test
    public void testMissingPredicateInPomReportsError() throws Exception {
        Path mappingPath = testResourcesDir.resolve("missing_predicate_in_pom/mapping.ttl");
        assertThat(mappingPath.toFile()).exists();

        RmlExecutionReport report = new RmlExecutionReport();
        RmlLoader loader = new RmlLoader(mappingPath)
                .withReport(report)
                .withValidationMode(ValidationMode.LENIENT);

        loader.loadMapping();

        assertThat(report.getErrors())
                .as("Should report error for POM missing predicate")
                .isNotEmpty();
    }

    @Test
    public void testMissingObjectInPomReportsError() throws Exception {
        Path mappingPath = testResourcesDir.resolve("missing_object_in_pom/mapping.ttl");
        assertThat(mappingPath.toFile()).exists();

        RmlExecutionReport report = new RmlExecutionReport();
        RmlLoader loader = new RmlLoader(mappingPath)
                .withReport(report)
                .withValidationMode(ValidationMode.LENIENT);

        loader.loadMapping();

        assertThat(report.getErrors())
                .as("Should report error for POM missing object")
                .isNotEmpty();
    }

    @Test
    public void testInvalidExpressionMapReportsError() throws Exception {
        Path mappingPath = testResourcesDir.resolve("invalid_expression_map/mapping.ttl");
        assertThat(mappingPath.toFile()).exists();

        RmlExecutionReport report = new RmlExecutionReport();
        RmlLoader loader = new RmlLoader(mappingPath)
                .withReport(report)
                .withValidationMode(ValidationMode.LENIENT);

        loader.loadMapping();

        assertThat(report.getErrors())
                .as("Should report error for empty SubjectMap")
                .isNotEmpty();
    }

    @Test
    public void testRomMissingParentTriplesMapReportsErrorAndUnpacksOr() throws Exception {
        Path mappingPath = testResourcesDir.resolve("rom_missing_parent_triples_map/mapping.ttl");
        assertThat(mappingPath.toFile()).exists();

        RmlExecutionReport report = new RmlExecutionReport();
        RmlLoader loader = new RmlLoader(mappingPath)
                .withReport(report)
                .withValidationMode(ValidationMode.LENIENT);

        loader.loadMapping();

        assertThat(report.getErrors())
                .as("Should report error for RefObjectMap missing parentTriplesMap")
                .isNotEmpty();

        String fullReportText = PlainTextReportGenerator.generateTextReport(report, false);

        // Verify sh:or in PredicateObjectMapShape (ObjectMapShape vs RefObjectMapShape) is unpacked
        assertThat(report.getErrors())
                .extracting(RmlError::message)
                .anySatisfy(msg ->
                        assertThat(msg)
                                .as("Report should explain sh:or failure between ObjectMap and RefObjectMap:\n" + fullReportText)
                                .containsAnyOf("Requires satisfying at least one of", "RefObjectMap", RML.parentTriplesMap.getLocalName())
                );
    }

    @Test
    public void testInvalidTemplateInNtriplesReportsReferenceFormulationSyntaxError() throws Exception {
        Path mappingPath = testResourcesDir.resolve("invalid_template_ntriples/mapping.nt");
        assertThat(mappingPath.toFile()).exists();

        RmlExecutionReport report = new RmlExecutionReport();
        RmlLoader loader = new RmlLoader(mappingPath)
                .withReport(report)
                .withValidationMode(ValidationMode.LENIENT);

        // In LENIENT mode, parsing does not throw; the error is recorded into the report,
        // and the template is left with no segments.
        loader.loadMapping();

        assertThat(report.getErrors())
                .as("Should record error for invalid template in LENIENT mode")
                .isNotEmpty();

        assertThat(report.getErrors())
                .extracting(RmlError::errorType)
                .anySatisfy(errorType ->
                        assertThat(errorType.getURI())
                                .as("Error type should be ReferenceFormulationSyntaxError")
                                .contains("ReferenceFormulationSyntaxError")
                );

        assertThat(report.getErrors())
                .extracting(RmlError::message)
                .anySatisfy(msg ->
                        assertThat(msg)
                                .as("Error message should pinpoint offending characters and mention non-escaped braces")
                                .contains("{Name}")
                                .contains("has non escaped")
                );
    }

    @Test
    public void testInvalidTemplateInNtriplesThrowsInStrictMode() {
        Path mappingPath = testResourcesDir.resolve("invalid_template_ntriples/mapping.nt");
        assertThat(mappingPath.toFile()).exists();

        RmlExecutionReport report = new RmlExecutionReport();
        RmlLoader loader = new RmlLoader(mappingPath)
                .withReport(report)
                .withValidationMode(ValidationMode.STRICT);

        assertThatThrownBy(loader::loadMapping)
                .isInstanceOf(BurpException.class)
                .satisfies(ex -> {
                    RmlError error = ((BurpException) ex).getError();
                    assertThat(error.errorType().getURI())
                            .contains("ReferenceFormulationSyntaxError");
                    assertThat(error.message())
                            .contains("{Name}")
                            .contains("has non escaped");
                });
    }

    @Test
    public void testValidateShaclShapeMissingLogicalSourceViaServer() throws Exception {
        Path mappingPath = testResourcesDir.resolve("missing_logical_source/mapping.ttl");
        burp.dev.rml.RmlDevServerImpl server = new burp.dev.rml.RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(mappingPath.toUri().toString())).get();
        assertThat(diagnostics).anyMatch(d ->
                d.severity() == DiagnosticSeverity.Error &&
                d.message().toLowerCase().contains("logicalsource")
        );
    }

    @Test
    public void testValidateShaclShapeMissingSubjectMapViaServer() throws Exception {
        Path mappingPath = testResourcesDir.resolve("missing_subject_map/mapping.ttl");
        burp.dev.rml.RmlDevServerImpl server = new burp.dev.rml.RmlDevServerImpl();
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(mappingPath.toUri().toString())).get();
        assertThat(diagnostics).anyMatch(d ->
                d.severity() == DiagnosticSeverity.Error &&
                (d.message().contains("subjectMap") || d.message().contains("subject"))
        );
    }
}
