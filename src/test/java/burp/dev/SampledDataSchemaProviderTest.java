package burp.dev;

import burp.dev.rml.RmlDevServerImpl;
import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.completion.ReferenceSchemaResolver.SchemaInfo;
import burp.dev.rml.service.schema.SampledDataSchemaProvider;
import burp.dev.rml.service.validator.RmlDiagnostic;
import burp.dev.rml.service.validator.ValidateParams;
import burp.model.AbstractLogicalSource;
import burp.vocabularies.RER;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@NullMarked
public class SampledDataSchemaProviderTest {

    @Test
    public void testProgressiveSamplingLimits() throws Exception {
        MappingWorkspace workspace = new MappingWorkspace();
        Path mappingPath = Path.of("src/test/resources/burp/sample_limit/mapping.ttl").toAbsolutePath();
        workspace.setCurrentWorkingDirectory(mappingPath.getParent());

        var loaded = workspace.loadMappingDetailed(mappingPath.toUri().toString());
        AbstractLogicalSource logicalSource = loaded.document().triplesMaps.getFirst().logicalSource;
        assertThat(logicalSource).isNotNull();

        // 1. iterationSampleLimit = 1: only fields from first record appear
        SampledDataSchemaProvider provider1 = new SampledDataSchemaProvider(1);
        SchemaInfo schema1 = provider1.extractSchema(logicalSource);
        assertThat(schema1.candidateValues().keySet())
                .contains("$.name", "$.id")
                .doesNotContain("$.email");

        // 2. iterationSampleLimit = 2: new field 'email' from second record appears
        SampledDataSchemaProvider provider2 = new SampledDataSchemaProvider(2);
        SchemaInfo schema2 = provider2.extractSchema(logicalSource);
        assertThat(schema2.candidateValues().keySet())
                .contains("$.name", "$.id", "$.email");

        // 3. iterationSampleLimit = null (unlimited): exact same result as iterationSampleLimit = 2
        SampledDataSchemaProvider providerUnlimited = new SampledDataSchemaProvider(null);
        SchemaInfo schemaUnlimited = providerUnlimited.extractSchema(logicalSource);
        assertThat(schemaUnlimited.candidateValues()).isEqualTo(schema2.candidateValues());
    }

    @Test
    public void testReferenceValidationRespectsIterationSampleLimit() throws Exception {
        Path mappingPath = Path.of("src/test/resources/burp/sample_limit/mapping.ttl").toAbsolutePath();
        String uri = mappingPath.toUri().toString();

        RmlDevServerImpl server = new RmlDevServerImpl();
        server.getWorkspace().setCurrentWorkingDirectory(mappingPath.getParent());

        // In-memory update of mapping to reference late-appearing field $.email
        String mappingWithEmail = """
                @prefix ex: <http://example.org/> .
                @prefix rml: <http://w3id.org/rml/> .

                ex:PersonMapping a rml:TriplesMap ;
                    rml:logicalSource [ a rml:LogicalSource ;
                        rml:referenceFormulation rml:JSONPath ;
                        rml:iterator "$[*]" ;
                        rml:source [ a rml:RelativePathSource ;
                            rml:root rml:MappingDirectory ;
                            rml:path "data.json"
                        ]
                    ] ;
                    rml:subjectMap [
                        rml:template "http://example.org/person/{$.email}"
                    ] .
                """;

        server.getWorkspace().updateDocumentText(uri, mappingWithEmail);

        // With iterationSampleLimit = 1, $.email is not sampled -> warning emitted
        server.getWorkspace().setIterationSampleLimit(1);
        List<RmlDiagnostic> diags1 = server.validate(new ValidateParams(uri)).get();
        assertThat(diags1)
                .filteredOn(d -> d.severity() == DiagnosticSeverity.Warning
                        && RER.ReferenceFormulationExecutionError.getURI().equals(d.rerCodeUri()))
                .hasSize(1)
                .first()
                .satisfies(d -> assertThat(d.message()).contains("$.email"));

        // With iterationSampleLimit = 2, $.email is sampled -> no warning
        server.getWorkspace().setIterationSampleLimit(2);
        List<RmlDiagnostic> diags2 = server.validate(new ValidateParams(uri)).get();
        assertThat(diags2)
                .filteredOn(d -> d.severity() == DiagnosticSeverity.Warning
                        && RER.ReferenceFormulationExecutionError.getURI().equals(d.rerCodeUri()))
                .isEmpty();

        // With iterationSampleLimit = null (unlimited), $.email is sampled -> no warning
        server.getWorkspace().setIterationSampleLimit(null);
        List<RmlDiagnostic> diagsUnlimited = server.validate(new ValidateParams(uri)).get();
        assertThat(diagsUnlimited)
                .filteredOn(d -> d.severity() == DiagnosticSeverity.Warning
                        && RER.ReferenceFormulationExecutionError.getURI().equals(d.rerCodeUri()))
                .isEmpty();
    }
}
