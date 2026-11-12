package burp.dev.ontology;

import burp.dev.rml.service.ontology.OntologyRegistry;
import burp.dev.rml.service.validator.RuntimeOntologyValidator;
import burp.model.RdfPredicateObject;
import burp.model.SubjectMap;
import burp.model.TriplesMap;
import burp.model.rdf.IRITerm;
import burp.model.rdf.LiteralTerm;
import burp.reporting.RmlError;
import burp.vocabularies.RER;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class RuntimeOntologyValidatorTest {

    private static final Path ONTOLOGY_PATH = Path.of("src/test/resources/burp/ontology/runtime_ontology.ttl");

    private OntologyRegistry registry;
    private RuntimeOntologyValidator validator;
    private final Map<String, String> prefixMap = Map.of("ex", "http://example.org/", "xsd", "http://www.w3.org/2001/XMLSchema#");

    @BeforeEach
    public void setUp() throws Exception {
        registry = new OntologyRegistry();
        registry.loadOntology(ONTOLOGY_PATH);
        validator = new RuntimeOntologyValidator();
    }

    @Test
    public void testDynamicDomainMismatch() {
        TriplesMap tm = new TriplesMap();
        tm.subjectMap = new SubjectMap();
        tm.subjectMap.classes.add(ResourceFactory.createResource("http://example.org/Patient"));

        RdfPredicateObject generated = new RdfPredicateObject(
                new IRITerm("http://example.org/worksAt"),
                new IRITerm("http://example.org/CityHospital"),
                null,
                Set.of()
        );

        List<RmlError> errors = validator.validateResults(tm, List.of(generated), registry, prefixMap, 20);

        assertThat(errors)
                .filteredOn(e -> RER.OntologyDomainMismatch.equals(e.errorType()))
                .hasSize(1);
        assertThat(errors.getFirst().message())
                .contains("ex:worksAt")
                .contains("[sampled evaluation, limit 20]");
    }

    @Test
    public void testDynamicTermTypeMismatch() {
        TriplesMap tm = new TriplesMap();
        tm.subjectMap = new SubjectMap();
        tm.subjectMap.classes.add(ResourceFactory.createResource("http://example.org/Patient"));

        // Generated a literal for an ObjectProperty
        RdfPredicateObject generated = new RdfPredicateObject(
                new IRITerm("http://example.org/treatedBy"),
                new LiteralTerm("Dr. Bob"),
                null,
                Set.of()
        );

        List<RmlError> errors = validator.validateResults(tm, List.of(generated), registry, prefixMap, 10);

        assertThat(errors)
                .filteredOn(e -> RER.OntologyTermTypeMismatch.equals(e.errorType()))
                .hasSize(1);
        assertThat(errors.getFirst().message())
                .contains("ex:treatedBy")
                .contains("[sampled evaluation, limit 10]");
    }

    @Test
    public void testDynamicIllTypedLiteral() {
        TriplesMap tm = new TriplesMap();
        tm.subjectMap = new SubjectMap();
        tm.subjectMap.classes.add(ResourceFactory.createResource("http://example.org/Patient"));

        // Generated non-numeric string for xsd:integer
        RdfPredicateObject generated = new RdfPredicateObject(
                new IRITerm("http://example.org/patientId"),
                new LiteralTerm("abc", new IRITerm("http://www.w3.org/2001/XMLSchema#integer")),
                null,
                Set.of()
        );

        List<RmlError> errors = validator.validateResults(tm, List.of(generated), registry, prefixMap, 5);

        assertThat(errors)
                .filteredOn(e -> RER.OntologyDatatypeMismatch.equals(e.errorType()))
                .hasSize(1);
        assertThat(errors.getFirst().message())
                .contains("abc")
                .contains("xsd:integer")
                .contains("[sampled evaluation, limit 5]");
    }
}
