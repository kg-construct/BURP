package turtleprov;

import burp.parse.turtleprov.ProvStore;
import burp.parse.turtleprov.RDF12Converter;
import burp.parse.turtleprov.TurtleProvParser;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import turtleprov.manifest.RdfManifest;
import turtleprov.manifest.RdfTest;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractManifestTest {

    public abstract Path rootManifestFile();
    public abstract boolean filterRdf11();

    public static class TestInfo {
        public final String testUri;
        public final String name;
        public final Path actionFile;
        public final Path resultFile;

        public TestInfo(String testUri, String name, Path actionFile, Path resultFile) {
            this.testUri = testUri;
            this.name = name;
            this.actionFile = actionFile;
            this.resultFile = resultFile;
        }

        @Override
        public String toString() {
            return name + " (" + actionFile.getFileName() + ")";
        }
    }

    // Recursively find all manifests starting from a root manifest file
    private List<Path> loadAllManifests(Path manifestFile, Set<Path> visited) {
        Path canonical = manifestFile.toAbsolutePath().normalize();
        if (!visited.add(canonical)) return Collections.emptyList();

        // If filterRdf11 is true, skip loading manifest files under rdf11/
        if (filterRdf11() && canonical.toString().contains("rdf11/")) {
            return Collections.emptyList();
        }

        List<Path> list = new ArrayList<>();
        list.add(canonical);

        Model model;
        try {
            model = RDFDataMgr.loadModel(canonical.toString());
        } catch (Exception e) {
            return list;
        }

        Property includeProp = model.createProperty(RdfManifest.include);
        Resource manifestType = model.createResource(RdfManifest.Manifest);
        var manifests = model.listResourcesWithProperty(RDF.type, manifestType);

        while (manifests.hasNext()) {
            Resource m = manifests.next();
            var prop = m.getProperty(includeProp);
            if (prop != null && prop.getObject() != null) {
                var includeNode = prop.getObject();
                if (includeNode.canAs(RDFList.class)) {
                    RDFList rdfList = includeNode.as(RDFList.class);
                    for (var node : rdfList.asJavaList()) {
                        if (node.isResource()) {
                            String uri = node.asResource().getURI();
                            if (uri != null && uri.startsWith("file:")) {
                                Path file = Paths.get(URI.create(uri));
                                list.addAll(loadAllManifests(file, visited));
                            }
                        }
                    }
                }
            }
        }
        return list;
    }

    private List<TestInfo> getTestsOfType(String typeUri) {
        List<Path> manifests = loadAllManifests(rootManifestFile(), new HashSet<>());
        List<TestInfo> list = new ArrayList<>();

        String queryStr = 
            "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
            "PREFIX mf:   <" + RdfManifest.NS + ">\n" +
            "PREFIX rdft: <" + RdfTest.NS + ">\n" +
            "\n" +
            "SELECT ?test ?name ?action ?result WHERE {\n" +
            "  ?manifest rdf:type mf:Manifest .\n" +
            "  ?manifest mf:entries ?entries .\n" +
            "  ?entries rdf:rest*/rdf:first ?test .\n" +
            "  ?test rdf:type <" + typeUri + "> .\n" +
            "  ?test mf:name ?name .\n" +
            "  ?test mf:action ?action .\n" +
            "  OPTIONAL { ?test mf:result ?result }\n" +
            "}";

        var query = QueryFactory.create(queryStr);

        for (Path manifestFile : manifests) {
            Model model = RDFDataMgr.loadModel(manifestFile.toString());
            try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
                ResultSet results = qexec.execSelect();
                while (results.hasNext()) {
                    var soln = results.nextSolution();
                    String testUri = soln.getResource("test").getURI();
                    String name = soln.getLiteral("name").getString();
                    Resource actionNode = soln.getResource("action");
                    Path actionFile = Paths.get(URI.create(actionNode.getURI()));
                    Resource resultNode = soln.getResource("result");
                    Path resultFile = resultNode != null ? Paths.get(URI.create(resultNode.getURI())) : null;
                    list.add(new TestInfo(testUri, name, actionFile, resultFile));
                }
            }
        }
        return list;
    }

    public Stream<Arguments> positiveSyntaxTests() {
        return getTestsOfType(RdfTest.TestTurtlePositiveSyntax)
            .stream().map(Arguments::of);
    }

    public Stream<Arguments> negativeSyntaxTests() {
        return getTestsOfType(RdfTest.TestTurtleNegativeSyntax)
            .stream().map(Arguments::of);
    }

    public Stream<Arguments> evalTests() {
        return getTestsOfType(RdfTest.TestTurtleEval)
            .stream().map(Arguments::of);
    }

    public Stream<Arguments> negativeEvalTests() {
        return getTestsOfType(RdfTest.TestTurtleNegativeEval)
            .stream().map(Arguments::of);
    }



    @ParameterizedTest(name = "{0}")
    @MethodSource("positiveSyntaxTests")
    public void testPositiveSyntax(TestInfo info) throws Exception {
        TurtleProvParser.parseTurtleFromString(Files.readString(info.actionFile));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("negativeSyntaxTests")
    public void testNegativeSyntax(TestInfo info) throws Exception {
        ProvStore store = TurtleProvParser.parseTurtleFromString(Files.readString(info.actionFile));
        Assertions.assertFalse(store.getSyntaxErrors().isEmpty(), "Expected syntax errors but none were found");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("evalTests")
    public void testEvaluation(TestInfo info) throws Exception {
        ProvStore store = TurtleProvParser.parseTurtleFromString(Files.readString(info.actionFile));
        Model actionModel = RDF12Converter.toModel(store, false);
        Model expectedModel = RDFDataMgr.loadModel(info.resultFile.toString());
        Assertions.assertTrue(expectedModel.isIsomorphicWith(actionModel), () ->
            "Expected isomorphic models for test " + info.name + ".\nExpected:\n" + expectedModel + "\nActual:\n" + actionModel
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("negativeEvalTests")
    public void testNegativeEvaluation(TestInfo info) {
        try {
            ProvStore store = TurtleProvParser.parseTurtleFromString(Files.readString(info.actionFile));
            Assertions.assertTrue(store.triples.isEmpty() || !store.getSyntaxErrors().isEmpty());
        } catch (Exception e) {
            // Expected parsing/evaluation failure
            return;
        }
    }
}
