package burp.dev.ontology;

import burp.dev.rml.service.ontology.OntologyRegistry;
import burp.dev.rml.service.ontology.PropertyCandidate;
import burp.dev.rml.service.ontology.PropertyConstraint;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.XSD;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class OntologyRegistryTest {

    private static final Path ONTOLOGY_PATH = Path.of("src/test/resources/burp/ontology/hospital_ontology.ttl");

    private OntologyRegistry registry;

    @BeforeEach
    public void setUp() throws Exception {
        registry = new OntologyRegistry();
        registry.loadOntology(ONTOLOGY_PATH);
    }

    @Test
    public void testClassHierarchyDistances() {
        Resource patient = ResourceFactory.createResource("http://example.org/Patient");
        Map<Resource, Integer> distances = registry.computeClassHierarchyDistances(Set.of(patient));

        assertThat(distances)
                .containsEntry(patient, 0)
                .containsEntry(ResourceFactory.createResource("http://example.org/Person"), 1)
                .doesNotContainKey(ResourceFactory.createResource("http://example.org/Doctor"));
    }

    @Test
    public void testApplicablePropertiesInheritance() {
        Resource patient = ResourceFactory.createResource("http://example.org/Patient");
        Map<String, String> prefixMap = Map.of("ex", "http://example.org/");

        List<PropertyCandidate> props = registry.getApplicableProperties(Set.of(patient), prefixMap);

        assertThat(props)
                .extracting(PropertyCandidate::compactIri)
                .contains("ex:patientId", "ex:treatedBy", "ex:name")
                .doesNotContain("ex:worksAt");

        PropertyCandidate patientId = props.stream().filter(p -> "ex:patientId".equals(p.compactIri())).findFirst().orElseThrow();
        assertThat(patientId.inheritanceDistance()).isEqualTo(0);
        assertThat(patientId.isDatatypeProperty()).isTrue();
        assertThat(patientId.expectedRange()).isEqualTo(XSD.integer);

        PropertyCandidate name = props.stream().filter(p -> "ex:name".equals(p.compactIri())).findFirst().orElseThrow();
        assertThat(name.inheritanceDistance()).isEqualTo(1);
        assertThat(name.isDatatypeProperty()).isTrue();
    }

    @Test
    public void testPropertyConstraints() {
        Resource patient = ResourceFactory.createResource("http://example.org/Patient");

        PropertyConstraint patientIdConstraint = registry.getPropertyConstraint(
                Set.of(patient),
                ResourceFactory.createResource("http://example.org/patientId")
        );
        assertThat(patientIdConstraint.isKnownProperty()).isTrue();
        assertThat(patientIdConstraint.isDomainCompatible()).isTrue();
        assertThat(patientIdConstraint.isDatatypeProperty()).isTrue();

        PropertyConstraint worksAtConstraint = registry.getPropertyConstraint(
                Set.of(patient),
                ResourceFactory.createResource("http://example.org/worksAt")
        );
        assertThat(worksAtConstraint.isKnownProperty()).isTrue();
        assertThat(worksAtConstraint.isDomainCompatible()).isFalse();
    }
}
