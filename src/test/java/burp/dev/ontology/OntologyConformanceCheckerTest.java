package burp.dev.ontology;

import burp.dev.rml.service.ontology.OntologyConformanceChecker;
import burp.dev.rml.service.ontology.OntologyRegistry;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
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

public class OntologyConformanceCheckerTest {

    private static final Path ONTOLOGY_PATH = Path.of("src/test/resources/burp/ontology/conformance_ontology.ttl");

    private OntologyRegistry registry;
    private final Map<String, String> prefixMap = Map.of("ex", "http://example.org/", "xsd", "http://www.w3.org/2001/XMLSchema#");

    @BeforeEach
    public void setUp() throws Exception {
        registry = new OntologyRegistry();
        registry.loadOntology(ONTOLOGY_PATH);
    }

    @Test
    public void testDomainMismatchDetection() {
        Resource patient = ResourceFactory.createResource("http://example.org/Patient");
        Resource worksAt = ResourceFactory.createResource("http://example.org/worksAt");

        List<OntologyConformanceChecker.Issue> issues = OntologyConformanceChecker.check(
                Set.of(patient),
                worksAt,
                null,
                null,
                null,
                null,
                registry,
                prefixMap
        );

        assertThat(issues)
                .extracting(OntologyConformanceChecker.Issue::errorType)
                .containsExactly(RER.OntologyDomainMismatch);
        assertThat(issues.getFirst().message())
                .contains("ex:worksAt", "ex:Doctor");
    }

    @Test
    public void testTermTypeMismatchDetection() {
        Resource patient = ResourceFactory.createResource("http://example.org/Patient");
        Resource treatedBy = ResourceFactory.createResource("http://example.org/treatedBy");

        // Literal used on an ObjectProperty
        List<OntologyConformanceChecker.Issue> issues = OntologyConformanceChecker.check(
                Set.of(patient),
                treatedBy,
                RML.LITERAL,
                null,
                null,
                null,
                registry,
                prefixMap
        );

        assertThat(issues)
                .extracting(OntologyConformanceChecker.Issue::errorType)
                .containsExactly(RER.OntologyTermTypeMismatch);
        assertThat(issues.getFirst().message())
                .contains("ex:treatedBy", "rml:Literal");
    }

    @Test
    public void testDatatypeMismatchDetection() {
        Resource person = ResourceFactory.createResource("http://example.org/Person");
        Resource age = ResourceFactory.createResource("http://example.org/age");

        // xsd:boolean declared on an xsd:integer property
        List<OntologyConformanceChecker.Issue> issues = OntologyConformanceChecker.check(
                Set.of(person),
                age,
                null,
                XSD.xboolean,
                null,
                null,
                registry,
                prefixMap
        );

        assertThat(issues)
                .extracting(OntologyConformanceChecker.Issue::errorType)
                .containsExactly(RER.OntologyDatatypeMismatch);
        assertThat(issues.getFirst().message())
                .contains("ex:age", "xsd:boolean", "xsd:integer");
    }

    @Test
    public void testIllTypedLexicalValueDetection() {
        Resource person = ResourceFactory.createResource("http://example.org/Person");
        Resource age = ResourceFactory.createResource("http://example.org/age");

        // "not-a-number" provided as lexical value for xsd:integer
        List<OntologyConformanceChecker.Issue> issues = OntologyConformanceChecker.check(
                Set.of(person),
                age,
                null,
                null,
                "not-a-number",
                XSD.integer,
                registry,
                prefixMap
        );

        assertThat(issues)
                .extracting(OntologyConformanceChecker.Issue::errorType)
                .containsExactly(RER.OntologyDatatypeMismatch);
        assertThat(issues.getFirst().message())
                .contains("not-a-number", "xsd:integer");
    }

    @Test
    public void testDirectionalSubtyping_NarrowingAllowed() {
        Resource person = ResourceFactory.createResource("http://example.org/Person");
        Resource age = ResourceFactory.createResource("http://example.org/age");

        // xsd:nonNegativeInteger declared on xsd:integer range property is valid narrowing
        List<OntologyConformanceChecker.Issue> issues = OntologyConformanceChecker.check(
                Set.of(person),
                age,
                null,
                XSD.nonNegativeInteger,
                null,
                null,
                registry,
                prefixMap
        );

        assertThat(issues).isEmpty();
    }

    @Test
    public void testDirectionalSubtyping_WideningRejected() {
        Resource person = ResourceFactory.createResource("http://example.org/Person");
        Resource count = ResourceFactory.createResource("http://example.org/count");

        // xsd:integer declared on xsd:nonNegativeInteger range property is widening (invalid)
        List<OntologyConformanceChecker.Issue> issues = OntologyConformanceChecker.check(
                Set.of(person),
                count,
                null,
                XSD.integer,
                null,
                null,
                registry,
                prefixMap
        );

        assertThat(issues)
                .extracting(OntologyConformanceChecker.Issue::errorType)
                .containsExactly(RER.OntologyDatatypeMismatch);
        assertThat(issues.getFirst().message())
                .contains("xsd:integer", "xsd:nonNegativeInteger", "ex:count");
    }

    @Test
    public void testDirectionalSubtyping_DisjointRejected() {
        Resource person = ResourceFactory.createResource("http://example.org/Person");
        Resource count = ResourceFactory.createResource("http://example.org/count");

        // xsd:negativeInteger declared on xsd:nonNegativeInteger range property is disjoint
        List<OntologyConformanceChecker.Issue> issues = OntologyConformanceChecker.check(
                Set.of(person),
                count,
                null,
                XSD.negativeInteger,
                null,
                null,
                registry,
                prefixMap
        );

        assertThat(issues)
                .extracting(OntologyConformanceChecker.Issue::errorType)
                .containsExactly(RER.OntologyDatatypeMismatch);
    }

    @Test
    public void testDirectionalSubtyping_RuntimeNegativeValueRejected() {
        Resource person = ResourceFactory.createResource("http://example.org/Person");
        Resource count = ResourceFactory.createResource("http://example.org/count");

        // Evaluated value "-5" violates xsd:nonNegativeInteger expected range
        List<OntologyConformanceChecker.Issue> issues = OntologyConformanceChecker.check(
                Set.of(person),
                count,
                null,
                null,
                "-5",
                XSD.integer,
                registry,
                prefixMap
        );

        assertThat(issues)
                .extracting(OntologyConformanceChecker.Issue::errorType)
                .contains(RER.OntologyDatatypeMismatch);
    }
}
