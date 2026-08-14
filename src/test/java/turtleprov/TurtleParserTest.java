package turtleprov;

import burp.parse.turtleprov.ProvStore;
import burp.parse.turtleprov.ProvTriple;
import burp.parse.turtleprov.TurtleNodeKind;
import burp.parse.turtleprov.TurtleProvParser;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.vocabulary.XSD;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class TurtleParserTest {

    private static ProvStore parseTurtleFromString(String turtleContent) {
        return TurtleProvParser.parseTurtleFromString(turtleContent);
    }

    @Test
    public void testParsesASimpleTurtleTripleWithProvenance() {
        String turtle =
                """
                        @prefix ex: <http://example.com/> .
                        ex:alice ex:knows ex:bob .
                        """;

        ProvStore store = parseTurtleFromString(turtle);

        assertEquals(1, store.getTriples().size());
        assertEquals("http://example.com/", store.getPrefixes().get("ex"));

        ProvTriple provTriple = store.getTriples().iterator().next();
        assertEquals("http://example.com/alice", provTriple.statement().getSubject().getURI());
        assertEquals("http://example.com/knows", provTriple.statement().getPredicate().getURI());
        assertEquals("http://example.com/bob", provTriple.statement().getObject().asResource().getURI());

        assertNotNull(provTriple.subjectInfo());
        assertNotNull(provTriple.predicateInfo());
        assertNotNull(provTriple.objectInfo());
    }

    @Test
    public void testParsesTypedLiteral() {
        String turtle = """
                @prefix ex: <http://example.com/> .
                ex:alice ex:age 42 .
                """;

        ProvStore store = parseTurtleFromString(turtle);

        assertEquals(1, store.getTriples().size());
        var obj = store.getTriples().iterator().next().statement().getObject();
        assertTrue(obj.isLiteral());
        Literal literal = obj.asLiteral();
        assertEquals("42", literal.getLexicalForm());
        assertEquals("http://www.w3.org/2001/XMLSchema#integer", literal.getDatatypeURI());
    }

    @Test
    public void testParsesRMLTC0000JSONFixtureInCommonTest() {
        String fixture =
                """
                        @prefix foaf: <http://xmlns.com/foaf/0.1/> .
                        @prefix rml: <http://w3id.org/rml/> .
                        
                        <http://example.com/base/TriplesMap1> a rml:TriplesMap;
                          rml:logicalSource [ a rml:LogicalSource;
                              rml:iterator "$.students[*]";
                              rml:referenceFormulation rml:JSONPath;
                              rml:source [ a rml:RelativePathSource;
                                  rml:root rml:MappingDirectory;
                                  rml:path "student.json"
                                ]
                            ];
                          rml:predicateObjectMap [
                              rml:objectMap [
                                  rml:reference "$.Name"
                                ];
                              rml:predicate foaf:name
                            ];
                          rml:subjectMap [
                              rml:template "http://example.com/{$.Name}"
                            ] .
                        """;

        ProvStore store = parseTurtleFromString(fixture);

        assertEquals("http://xmlns.com/foaf/0.1/", store.getPrefixes().get("foaf"));
        assertEquals("http://w3id.org/rml/", store.getPrefixes().get("rml"));
        assertTrue(store.getTriples().size() >= 8);

        boolean found = false;
        for (ProvTriple provTriple : store.getTriples()) {
            if ("http://example.com/base/TriplesMap1".equals(provTriple.statement().getSubject().getURI()) &&
                    "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".equals(provTriple.statement().getPredicate().getURI()) &&
                    provTriple.statement().getObject().isResource() &&
                    "http://w3id.org/rml/TriplesMap".equals(provTriple.statement().getObject().asResource().getURI())) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    private static class ParseResult {
        final Literal literal;
        final ProvTriple provTriple;
        final String line;

        ParseResult(Literal literal, ProvTriple provTriple, String line) {
            this.literal = literal;
            this.provTriple = provTriple;
            this.line = line;
        }
    }

    private ParseResult parseSingleLiteral(String predicateLocalName, String objectSyntax) {
        String turtle = """
                @prefix ex: <http://example.com/> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                ex:s ex:%s %s .
                """.formatted(predicateLocalName, objectSyntax);

        ProvStore store = parseTurtleFromString(turtle);
        ProvTriple matched = null;
        for (ProvTriple t : store.getTriples()) {
            if (("http://example.com/" + predicateLocalName).equals(t.statement().getPredicate().getURI())) {
                matched = t;
                break;
            }
        }
        assertNotNull(matched);
        Literal literal = matched.statement().getObject().asLiteral();
        String targetLine = turtle.split("\n")[2];
        return new ParseResult(literal, matched, targetLine);
    }

    private void assertLiteralObjectProvenance(
            ProvTriple provTriple,
            String line,
            TurtleNodeKind expectedKind,
            String expectedLiteralToken,
            String expectedLiteralContent
    ) {
        var objectInfo = provTriple.objectInfo();
        assertNotNull(objectInfo);
        var start = objectInfo.start();
        var end = objectInfo.end();
        assertNotNull(start);
        assertNotNull(end);

        assertEquals(expectedKind, objectInfo.kind());
        assertEquals(2, start.line());
        assertEquals(2, end.line());
        assertTrue(start.column() <= end.column());
        assertEquals(expectedLiteralToken, line.substring(start.column(), end.column()));

        var stringStart = objectInfo.rdfLiteralStringStart();
        var stringEnd = objectInfo.rdfLiteralStringEnd();
        assertNotNull(stringStart);
        assertNotNull(stringEnd);
        assertEquals(2, stringStart.line());
        assertEquals(2, stringEnd.line());
        assertTrue(stringStart.column() <= stringEnd.column());
        // FIXME Not a correct test due to characters to escape.
        // assertEquals(expectedLiteralContent, line.substring(stringStart.column(), stringEnd.column()));
    }

    @Test
    public void testParsesRdfLiteralWithDoubleQuotesAndProvenanceSpans() {
        ParseResult res = parseSingleLiteral("plain", "\"double\"");
        assertEquals("double", res.literal.getLexicalForm());
        assertEquals(XSD.xstring.getURI(), res.literal.getDatatypeURI());
        assertLiteralObjectProvenance(
                res.provTriple,
                res.line,
                TurtleNodeKind.STRING_LITERAL_QUOTE,
                "\"double\"",
                "double"
        );
    }

    @Test
    public void testParsesRdfLiteralWithSingleQuotesAndProvenanceSpans() {
        ParseResult res = parseSingleLiteral("single", "'single\\n'");
        assertEquals("single\n", res.literal.getLexicalForm());
        assertEquals(XSD.xstring.getURI(), res.literal.getDatatypeURI());
        assertLiteralObjectProvenance(
                res.provTriple,
                res.line,
                TurtleNodeKind.STRING_LITERAL_SINGLE_QUOTE,
                "'single\\n'",
                "single\n"
        );
    }

    @Test
    public void testParsesRdfLiteralWithLongDoubleQuotesAndProvenanceSpans() {
        ParseResult res = parseSingleLiteral("longDouble", "\"\"\"long double\"\"\"");
        assertEquals("long double", res.literal.getLexicalForm());
        assertEquals(XSD.xstring.getURI(), res.literal.getDatatypeURI());
        assertLiteralObjectProvenance(
                res.provTriple,
                res.line,
                TurtleNodeKind.STRING_LITERAL_LONG_QUOTE,
                "\"\"\"long double\"\"\"",
                "long double"
        );
    }

    @Test
    public void testParsesRdfLiteralWithLongSingleQuotesAndProvenanceSpans() {
        ParseResult res = parseSingleLiteral("longSingle", "'''long single'''");
        assertEquals("long single", res.literal.getLexicalForm());
        assertEquals(XSD.xstring.getURI(), res.literal.getDatatypeURI());
        assertLiteralObjectProvenance(
                res.provTriple,
                res.line,
                TurtleNodeKind.STRING_LITERAL_LONG_SINGLE_QUOTE,
                "'''long single'''",
                "long single"
        );
    }

    @Test
    public void testParsesRdfLiteralWithLanguageTagAndProvenanceSpans() {
        ParseResult res = parseSingleLiteral("lang", "\"Bonjour\"@fr");
        assertEquals("Bonjour", res.literal.getLexicalForm());
        assertEquals("fr", res.literal.getLanguage());
        assertLiteralObjectProvenance(
                res.provTriple,
                res.line,
                TurtleNodeKind.STRING_LITERAL_QUOTE,
                "\"Bonjour\"@fr",
                "Bonjour"
        );
    }

    @Test
    public void testParsesRdfLiteralWithDatatypeAndProvenanceSpans() {
        ParseResult res = parseSingleLiteral("typed", "\"42\"^^xsd:integer");
        assertEquals("42", res.literal.getLexicalForm());
        assertEquals("http://www.w3.org/2001/XMLSchema#integer", res.literal.getDatatypeURI());
        assertLiteralObjectProvenance(
                res.provTriple,
                res.line,
                TurtleNodeKind.STRING_LITERAL_QUOTE,
                "\"42\"^^xsd:integer",
                "42"
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rmlMappingFiles")
    public void testParsesRmlMappingFiles(Path mappingFile) throws Exception {
        String content = Files.readString(mappingFile);
        ProvStore store = parseTurtleFromString(content);

        assertFalse(store.getTriples().isEmpty(),
                "Expected some triples to be parsed in " + mappingFile);
    }


    public static Stream<Arguments> rmlMappingFiles() throws Exception {
        Path rmlResourcesPath = Paths.get("rml-modules");
        Stream<Arguments> rmlMappings;
        try (Stream<Path> walk = Files.walk(rmlResourcesPath)) {
            rmlMappings = walk
                    .filter(p -> p.toString().contains("rml-"))
                    .filter(p -> p.toString().endsWith("mapping.ttl"))
                    .map(Arguments::of)
                    .toList()
                    .stream();
        }

        Path burpResourcesPath = Paths.get("src/test/resources/burp");
        Stream<Arguments> burpMappings;
        try (Stream<Path> walk = Files.walk(burpResourcesPath)) {
            burpMappings = walk
                    .filter(p -> p.toString().endsWith("mapping.ttl"))
                    .map(Arguments::of)
                    .toList()
                    .stream();
        }

        return Stream.concat(rmlMappings, burpMappings);

    }

    @Test
    public void testSyntaxErrorReportingPointRange() {
        String invalidTurtle = "@prefix ex: <http://example.com/> .\nex:alice ex:knows .";
        ProvStore store = TurtleProvParser.parseTurtleFromString(invalidTurtle);

        assertFalse(store.getSyntaxErrors().isEmpty());
        var error = store.getSyntaxErrors().getFirst();
        assertNotNull(error.range());

        assertEquals(1, error.range().start().line);
        assertEquals(18, error.range().start().column);
        assertNotNull(error.range().end());
    }

    @Test
    public void testLexerSyntaxErrorReportingQuotedText() {
        String invalidTurtle = "@prefix ex: <http://example.com/> .\nex:alice ex:knows \"http://example.com/{Name\\}\" .";
        ProvStore store = TurtleProvParser.parseTurtleFromString(invalidTurtle);

        assertFalse(store.getSyntaxErrors().isEmpty());
        var error = store.getSyntaxErrors().getFirst();
        assertNotNull(error.range());

        assertEquals(1, error.range().start().line);
        assertEquals(18, error.range().start().column);

        // The extracted quoted text is "http://example.com/{Name\} (length 27)
        assertNotNull(error.range().end());
        assertEquals(1, error.range().end().line);
        assertEquals(45, error.range().end().column);
    }
}
