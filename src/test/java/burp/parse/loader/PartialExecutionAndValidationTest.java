package burp.parse.loader;

import burp.ls.CSVIteration;
import burp.model.*;
import burp.model.context.EvaluationContext;
import burp.model.rdf.IRITerm;
import burp.model.rdf.LiteralTerm;
import burp.model.rdf.Term;
import burp.parse.RmlLoader;
import burp.parse.ValidationMode;
import burp.reporting.BurpException;
import burp.reporting.RmlExecutionReport;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PartialExecutionAndValidationTest {

    private Model loadTurtle(String turtle) {
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, new StringReader(turtle), null, Lang.TURTLE);
        return model;
    }

    private CSVIteration createTestIteration(Map<String, String> data) {
        String[] headers = data.keySet().toArray(new String[0]);
        String[] values = data.values().toArray(new String[0]);
        return new CSVIteration(headers, values, Set.of());
    }

    @Test
    public void testStrictBatchModeFailsOnIncompleteMapping() {
        // Missing required predicate in POM - invalid under SHACL shapes
        String turtle = """
                PREFIX rml: <http://w3id.org/rml/>
                PREFIX ex: <http://example.com/>

                ex:TriplesMapIncomplete a rml:TriplesMap ;
                    rml:logicalSource [
                        rml:source [ a rml:RelativePathSource ; rml:path "student.csv" ] ;
                        rml:referenceFormulation rml:CSV
                    ] ;
                    rml:subjectMap [
                        rml:template "http://example.com/Student/{id}"
                    ] ;
                    rml:predicateObjectMap [
                        rml:objectMap [
                            rml:reference "name"
                        ]
                    ] .
                """;
        Model model = loadTurtle(turtle);
        RmlExecutionReport report = new RmlExecutionReport();

        // In strict batch mode: must fail at validation stage
        assertThrows(BurpException.class, () -> {
            new RmlLoader(model).withReport(report).loadMapping();
        });
        assertFalse(report.getErrors().isEmpty());
    }

    @Test
    public void testLenientDevServerModeSucceedsOnIncompleteMapping() throws Exception {
        // Same incomplete mapping should parse successfully in lenient mode
        String turtle = """
                PREFIX rml: <http://w3id.org/rml/>
                PREFIX ex: <http://example.com/>

                ex:TriplesMapIncomplete a rml:TriplesMap ;
                    rml:logicalSource [
                        rml:source [ a rml:RelativePathSource ; rml:path "student.csv" ] ;
                        rml:referenceFormulation rml:CSV
                    ] ;
                    rml:subjectMap [
                        rml:template "http://example.com/Student/{id}"
                    ] ;
                    rml:predicateObjectMap [
                        rml:objectMap [
                            rml:reference "name"
                        ]
                    ] .
                """;
        Model model = loadTurtle(turtle);
        RmlExecutionReport report = new RmlExecutionReport();

        RmlLoader lenientLoader = new RmlLoader(model).withReport(report).withValidationMode(ValidationMode.LENIENT);
        List<TriplesMap> triplesMaps = lenientLoader.loadMapping();
        assertNotNull(triplesMaps);
        assertEquals(1, triplesMaps.size());

        TriplesMap tm = triplesMaps.getFirst();
        assertNotNull(tm.subjectMap);
        assertEquals(1, tm.predicateObjectMaps.size());

        PredicateObjectMap pom = tm.predicateObjectMaps.getFirst();
        assertTrue(pom.predicateMaps.isEmpty());
        assertFalse(pom.isComplete());
        assertEquals(1, pom.objectMaps.size());

        // Verify AST parent wiring
        assertSame(tm, tm.subjectMap.getParent());
        assertSame(tm, pom.getParent());
        assertSame(pom, pom.objectMaps.getFirst().getParent());
    }

    @Test
    public void testEvaluateObjectMapInIsolation() throws Exception {
        String turtle = """
                PREFIX rml: <http://w3id.org/rml/>
                PREFIX ex: <http://example.com/>

                ex:TriplesMapIncomplete a rml:TriplesMap ;
                    rml:logicalSource [
                        rml:source [ a rml:RelativePathSource ; rml:path "student.csv" ] ;
                        rml:referenceFormulation rml:CSV
                    ] ;
                    rml:subjectMap [
                        rml:template "http://example.com/Student/{id}"
                    ] ;
                    rml:predicateObjectMap [
                        rml:objectMap [
                            rml:reference "name"
                        ]
                    ] .
                """;
        Model model = loadTurtle(turtle);
        List<TriplesMap> triplesMaps = new RmlLoader(model).withValidationMode(ValidationMode.LENIENT).loadMapping();
        TriplesMap tm = triplesMaps.getFirst();

        PredicateObjectMap pom = tm.predicateObjectMaps.getFirst();
        ObjectMap om = (ObjectMap) pom.objectMaps.getFirst();

        Iteration iteration = createTestIteration(Map.of("id", "10", "name", "Alice"));
        EvaluationContext context = EvaluationContext.createDefault();

        // Directly evaluate the ObjectMap even though predicate is missing in the POM
        List<Term> terms = om.generateTerms(iteration, context);
        assertEquals(1, terms.size());
        assertInstanceOf(LiteralTerm.class, terms.getFirst());
        assertEquals("Alice", ((LiteralTerm) terms.getFirst()).value());
    }

    @Test
    public void testEvaluatePredicateMapInIsolation() throws Exception {
        String turtle = """
                PREFIX rml: <http://w3id.org/rml/>
                PREFIX ex: <http://example.com/>

                ex:TriplesMapIncomplete a rml:TriplesMap ;
                    rml:logicalSource [
                        rml:source [ a rml:RelativePathSource ; rml:path "student.csv" ] ;
                        rml:referenceFormulation rml:CSV
                    ] ;
                    rml:subjectMap [
                        rml:template "http://example.com/Student/{id}"
                    ] ;
                    rml:predicateObjectMap [
                        rml:predicate ex:knows
                    ] .
                """;
        Model model = loadTurtle(turtle);
        List<TriplesMap> triplesMaps = new RmlLoader(model).withValidationMode(ValidationMode.LENIENT).loadMapping();
        TriplesMap tm = triplesMaps.getFirst();

        PredicateObjectMap pom = tm.predicateObjectMaps.getFirst();
        assertEquals(1, pom.predicateMaps.size());
        assertTrue(pom.objectMaps.isEmpty());

        PredicateMap pm = pom.predicateMaps.getFirst();
        Iteration iteration = createTestIteration(Map.of("id", "10"));
        EvaluationContext context = EvaluationContext.createDefault();

        // Directly evaluate the PredicateMap even though object is missing in the POM
        List<Term> terms = pm.generateTerms(iteration, context);
        assertEquals(1, terms.size());
        assertInstanceOf(IRITerm.class, terms.getFirst());
        assertEquals("http://example.com/knows", ((IRITerm) terms.getFirst()).uri());
    }

    @Test
    public void testPartialTriplesMapGenerationWithIncompleteSiblingPOM() throws Exception {
        String turtle = """
                PREFIX rml: <http://w3id.org/rml/>
                PREFIX ex: <http://example.com/>

                ex:TriplesMapPartial a rml:TriplesMap ;
                    rml:logicalSource [
                        rml:source [ a rml:RelativePathSource ; rml:path "student.csv" ] ;
                        rml:referenceFormulation rml:CSV
                    ] ;
                    rml:subjectMap [
                        rml:template "http://example.com/Student/{id}"
                    ] ;
                    rml:predicateObjectMap [
                        rml:predicate ex:name ;
                        rml:object "Alice"
                    ] ;
                    rml:predicateObjectMap [
                        rml:object "Unfinished POM with no predicate"
                    ] .
                """;
        Model model = loadTurtle(turtle);
        List<TriplesMap> triplesMaps = new RmlLoader(model).withValidationMode(ValidationMode.LENIENT).loadMapping();
        TriplesMap tm = triplesMaps.getFirst();

        assertEquals(2, tm.predicateObjectMaps.size());
        PredicateObjectMap completePom = tm.predicateObjectMaps.stream().filter(PredicateObjectMap::isComplete).findFirst().orElseThrow();
        PredicateObjectMap incompletePom = tm.predicateObjectMaps.stream().filter(p -> !p.isComplete()).findFirst().orElseThrow();

        assertTrue(completePom.isComplete());
        assertFalse(incompletePom.isComplete());

        Iteration iteration = createTestIteration(Map.of("id", "10"));
        EvaluationContext context = EvaluationContext.createDefault();

        // Complete POM evaluates and generates statements normally
        List<RdfPredicateObject> pos = completePom.generate(iteration, context);
        assertEquals(1, pos.size());
        assertEquals("http://example.com/name", pos.getFirst().predicate().uri());
        assertEquals("Alice", ((LiteralTerm) pos.getFirst().object()).value());

        // Incomplete POM returns empty list safely without throwing errors
        List<RdfPredicateObject> incompletePos = incompletePom.generate(iteration, context);
        assertTrue(incompletePos.isEmpty());
    }
}
