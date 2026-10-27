package burp.parse.loader;

import burp.model.*;
import burp.model.rdf.LiteralTerm;
import burp.parse.RmlLoader;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

public class ReflectiveRdfObjectLoaderTest {

    private ReflectiveRdfObjectLoader loader;

    @BeforeEach
    public void setup() {
        loader = new RmlLoader("").getReflectiveLoader();
    }

    private Model loadTurtle(String turtle) {
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, new StringReader(turtle), null, Lang.TURTLE);
        return model;
    }

    @Test
    public void testLoadTriplesMapWithShortcuts() {
        String turtle = """
                PREFIX rml: <http://w3id.org/rml/>
                PREFIX ex: <http://example.com/>

                ex:TriplesMap1
                    rml:subject ex:Person1 ;
                    rml:predicateObjectMap [
                        rml:predicate ex:name ;
                        rml:object "Alice"
                    ] .
                """;
        Model model = loadTurtle(turtle);
        Resource tmRes = model.getResource("http://example.com/TriplesMap1");

        TriplesMap tm = loader.map(model, tmRes, TriplesMap.class);
        assertNotNull(tm);
        assertNotNull(tm.subjectMap);
        assertInstanceOf(RDFNodeConstant.class, tm.subjectMap.expression);

        assertEquals(1, tm.predicateObjectMaps.size());
        PredicateObjectMap pom = tm.predicateObjectMaps.getFirst();
        assertEquals(1, pom.predicateMaps.size());
        assertEquals(1, pom.objectMaps.size());

        assertInstanceOf(ObjectMap.class, pom.objectMaps.getFirst());
        ObjectMap om = (ObjectMap) pom.objectMaps.getFirst();
        assertInstanceOf(RDFNodeConstant.class, om.expression);
        RDFNodeConstant constExpr = (RDFNodeConstant) om.expression;
        assertInstanceOf(LiteralTerm.class, constExpr.constant);
        assertEquals("Alice", ((LiteralTerm) constExpr.constant).value());
    }

    @Test
    public void testLoadTemplateSubjectMap() {
        String turtle = """
                PREFIX rml: <http://w3id.org/rml/>
                PREFIX ex: <http://example.com/>

                ex:TriplesMap2
                    rml:subjectMap [
                        rml:template "http://example.com/person/{id}" ;
                        rml:class ex:Person
                    ] .
                """;
        Model model = loadTurtle(turtle);
        Resource tmRes = model.getResource("http://example.com/TriplesMap2");

        TriplesMap tm = loader.map(model, tmRes, TriplesMap.class);
        assertNotNull(tm);
        assertNotNull(tm.subjectMap);
        assertInstanceOf(Template.class, tm.subjectMap.expression);
        Template template = (Template) tm.subjectMap.expression;
        assertEquals("http://example.com/person/{id}", template.getTemplate());
        assertEquals(1, tm.subjectMap.classes.size());
        assertEquals("http://example.com/Person", tm.subjectMap.classes.getFirst().getURI());
    }

    @Test
    public void testPolymorphicReferencingObjectMap() {
        String turtle = """
                PREFIX rml: <http://w3id.org/rml/>
                PREFIX ex: <http://example.com/>

                ex:TriplesMapParent a rml:TriplesMap .

                ex:TriplesMap3
                    rml:predicateObjectMap [
                        rml:predicate ex:knows ;
                        rml:objectMap [
                            rml:parentTriplesMap ex:TriplesMapParent
                        ]
                    ] .
                """;
        Model model = loadTurtle(turtle);
        Resource tmRes = model.getResource("http://example.com/TriplesMap3");

        TriplesMap tm = loader.map(model, tmRes, TriplesMap.class);
        assertNotNull(tm);
        assertEquals(1, tm.predicateObjectMaps.size());
        PredicateObjectMap pom = tm.predicateObjectMaps.getFirst();
        assertEquals(1, pom.objectMaps.size());

        // Polymorphic instantiation into ReferencingObjectMap
        assertInstanceOf(ReferencingObjectMap.class, pom.objectMaps.getFirst());
        ReferencingObjectMap rom = (ReferencingObjectMap) pom.objectMaps.getFirst();
        assertEquals("http://example.com/TriplesMapParent", rom.parentTriplesMap.subject.getURI());
    }

    @Test
    public void testPartialParsingMissingPredicateMap() {
        String turtle = """
                PREFIX rml: <http://w3id.org/rml/>
                PREFIX ex: <http://example.com/>

                ex:TriplesMapPartial
                    rml:predicateObjectMap [
                        rml:objectMap [
                            rml:template "http://example.com/item/{itemId}"
                        ]
                    ] .
                """;
        Model model = loadTurtle(turtle);
        Resource tmRes = model.getResource("http://example.com/TriplesMapPartial");

        TriplesMap tm = loader.map(model, tmRes, TriplesMap.class);
        assertNotNull(tm);
        assertEquals(1, tm.predicateObjectMaps.size());

        PredicateObjectMap pom = tm.predicateObjectMaps.getFirst();
        assertTrue(pom.predicateMaps.isEmpty());
        assertFalse(pom.isComplete());
        assertEquals(1, pom.objectMaps.size());

        assertInstanceOf(ObjectMap.class, pom.objectMaps.getFirst());
        ObjectMap om = (ObjectMap) pom.objectMaps.getFirst();
        assertInstanceOf(Template.class, om.expression);

        // ObjectMap should be directly evaluatable in isolation
        Template tpl = (Template) om.expression;
        assertEquals("http://example.com/item/{itemId}", tpl.getTemplate());
    }

    @Test
    public void testPartialParsingMissingSubjectMap() {
        String turtle = """
                PREFIX rml: <http://w3id.org/rml/>
                PREFIX ex: <http://example.com/>

                ex:TriplesMapNoSubject
                    rml:predicateObjectMap [
                        rml:predicate ex:name ;
                        rml:object "Bob"
                    ] .
                """;
        Model model = loadTurtle(turtle);
        Resource tmRes = model.getResource("http://example.com/TriplesMapNoSubject");

        TriplesMap tm = loader.map(model, tmRes, TriplesMap.class);
        assertNotNull(tm);
        assertNull(tm.subjectMap); // subjectMap is null because missing in mapping
        assertEquals(1, tm.predicateObjectMaps.size()); // POM is still parsed
    }
}
