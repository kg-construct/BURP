package burp.dev.ontology;

import burp.dev.rml.service.ontology.OntologyRegistry;
import burp.dev.rml.service.validator.OntologyValidator;
import burp.dev.rml.service.validator.RmlDiagnostic;
import burp.model.*;
import burp.model.rdf.IRITerm;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.XSD;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class OntologyValidatorTest {

    private static final Path ONTOLOGY_PATH = Path.of("src/test/resources/burp/ontology/hospital_ontology.ttl");

    private OntologyRegistry registry;
    private OntologyValidator validator;
    private final Map<String, String> prefixMap = Map.of("ex", "http://example.org/", "xsd", "http://www.w3.org/2001/XMLSchema#");

    @BeforeEach
    public void setUp() throws Exception {
        registry = new OntologyRegistry();
        registry.loadOntology(ONTOLOGY_PATH);
        validator = new OntologyValidator();
    }

    @Test
    public void testDomainMismatchDiagnostic() {
        TriplesMap tm = new TriplesMap();
        tm.subjectMap = new SubjectMap();
        tm.subjectMap.classes.add(ResourceFactory.createResource("http://example.org/Patient"));

        PredicateObjectMap pom = new PredicateObjectMap();
        PredicateMap pm = new PredicateMap();
        pm.expression = new RDFNodeConstant(new IRITerm("http://example.org/worksAt"));
        pom.predicateMaps.add(pm);
        tm.predicateObjectMaps.add(pom);

        List<RmlDiagnostic> diagnostics = new ArrayList<>();
        validator.validate(List.of(tm), registry, prefixMap, diagnostics);

        assertThat(diagnostics)
                .filteredOn(d -> RER.OntologyDomainMismatch.getURI().equals(d.rerCodeUri()))
                .hasSize(1);
        assertThat(diagnostics.getFirst().message())
                .contains("ex:worksAt")
                .contains("ex:Doctor")
                .contains("ex:Patient");
    }

    @Test
    public void testTermTypeMismatchDiagnostic() {
        TriplesMap tm = new TriplesMap();
        tm.subjectMap = new SubjectMap();
        tm.subjectMap.classes.add(ResourceFactory.createResource("http://example.org/Patient"));

        PredicateObjectMap pom = new PredicateObjectMap();
        PredicateMap pm = new PredicateMap();
        pm.expression = new RDFNodeConstant(new IRITerm("http://example.org/treatedBy"));
        pom.predicateMaps.add(pm);

        ObjectMap om = new ObjectMap();
        om.termType = RML.Literal;
        pom.objectMaps.add(om);
        tm.predicateObjectMaps.add(pom);

        List<RmlDiagnostic> diagnostics = new ArrayList<>();
        validator.validate(List.of(tm), registry, prefixMap, diagnostics);

        assertThat(diagnostics)
                .filteredOn(d -> RER.OntologyTermTypeMismatch.getURI().equals(d.rerCodeUri()))
                .hasSize(1);
        assertThat(diagnostics.getFirst().message())
                .contains("rml:Literal")
                .contains("ex:treatedBy");
    }

    @Test
    public void testDatatypeMismatchDiagnostic() {
        TriplesMap tm = new TriplesMap();
        tm.subjectMap = new SubjectMap();
        tm.subjectMap.classes.add(ResourceFactory.createResource("http://example.org/Patient"));

        PredicateObjectMap pom = new PredicateObjectMap();
        PredicateMap pm = new PredicateMap();
        pm.expression = new RDFNodeConstant(new IRITerm("http://example.org/patientId"));
        pom.predicateMaps.add(pm);

        ObjectMap om = new ObjectMap();
        om.datatypeMap = new DatatypeMap();
        om.datatypeMap.expression = new RDFNodeConstant(new IRITerm(XSD.xstring.getURI()));
        pom.objectMaps.add(om);
        tm.predicateObjectMaps.add(pom);

        List<RmlDiagnostic> diagnostics = new ArrayList<>();
        validator.validate(List.of(tm), registry, prefixMap, diagnostics);

        assertThat(diagnostics)
                .filteredOn(d -> RER.OntologyDatatypeMismatch.getURI().equals(d.rerCodeUri()))
                .hasSize(1);
        assertThat(diagnostics.getFirst().message())
                .contains("xsd:string")
                .contains("xsd:integer");
    }
}
