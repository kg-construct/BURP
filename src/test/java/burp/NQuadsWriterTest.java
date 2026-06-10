package burp;


import burp.model.RdfStatement;
import burp.model.rdf.BlankNodeTerm;
import burp.model.rdf.IRITerm;
import burp.model.rdf.LiteralTerm;
import burp.vocabularies.RML;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NQuadsWriterTest {

    @Test
    public void writesUnsafeIriAndEscapedLiterals() {
        List<RdfStatement> statements = List.of(
                new RdfStatement(
                        new IRITerm("http://example.org/unsafe subject", Set.of()),
                        new IRITerm("http://example.org/p", Set.of()),
                        new IRITerm("http://example.org/unsafe object", Set.of()),
                        new IRITerm("http://example.org/unsafe graph", Set.of())
                ),
                new RdfStatement(
                        new BlankNodeTerm("node-1", Set.of()),
                        new IRITerm("http://example.org/label", Set.of()),
                        new LiteralTerm("line1\n\"quoted\"\\slash", null, "en", Set.of()),
                        new IRITerm(RML.defaultGraph.getURI(), Set.of())
                )
        );

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NQuadsWriter.write(output, statements);

        String serialized = output.toString(StandardCharsets.UTF_8);
        String expected = "<http://example.org/unsafe subject> <http://example.org/p> <http://example.org/unsafe object> <http://example.org/unsafe graph> .\n" +
                "_:node-1 <http://example.org/label> \"line1\\n\\\"quoted\\\"\\\\slash\"@en .\n";

        assertEquals(expected, serialized);
    }

    @Test
    public void omitsDefaultGraphFromQuadLine() {
        RdfStatement statement = new RdfStatement(
                new IRITerm("http://example.org/s", Set.of()),
                new IRITerm("http://example.org/p", Set.of()),
                new LiteralTerm("value", new IRITerm("http://example.org/type", Set.of()), null, Set.of()),
                new IRITerm(RML.defaultGraph.getURI(), Set.of())
        );

        String serialized = NQuadsWriter.serializeStatement(statement);

        assertEquals(
                "<http://example.org/s> <http://example.org/p> \"value\"^^<http://example.org/type> .",
                serialized
        );
    }

    @Test
    public void writeDatatypes() {
        RdfStatement statement = new RdfStatement(
                new IRITerm("http://example.org/s", Set.of()),
                new IRITerm("http://example.org/p", Set.of()),
                new LiteralTerm("100", new IRITerm("http://www.w3.org/2001/XMLSchema#double", Set.of()), null, Set.of())
        );

        String serialized = NQuadsWriter.serializeStatement(statement);

        assertEquals(
                "<http://example.org/s> <http://example.org/p> \"100\"^^<http://www.w3.org/2001/XMLSchema#double> .",
                serialized
        );
    }

    @Test
    public void writeDouble() {
        RdfStatement statement = new RdfStatement(
                new IRITerm("http://example.org/s", Set.of()),
                new IRITerm("http://example.org/p", Set.of()),
                new LiteralTerm("3.6E1", new IRITerm("http://www.w3.org/2001/XMLSchema#double", Set.of()), null, Set.of())
        );

        String serialized = NQuadsWriter.serializeStatement(statement);

        assertEquals(
                "<http://example.org/s> <http://example.org/p> \"3.6E1\"^^<http://www.w3.org/2001/XMLSchema#double> .",
                serialized
        );
    }

    @Test
    public void writeLanguageTag() {
        RdfStatement statement = new RdfStatement(
                new IRITerm("http://example.org/s", Set.of()),
                new IRITerm("http://example.org/p", Set.of()),
                new LiteralTerm("value", null, "en-GB", Set.of())
        );

        String serialized = NQuadsWriter.serializeStatement(statement);

        assertEquals(
                "<http://example.org/s> <http://example.org/p> \"value\"@en-GB .",
                serialized
        );
    }

    @Test
    public void omitsExplicitXsdStringDatatypeInLiteralSerialization() {
        RdfStatement statement = new RdfStatement(
                new IRITerm("http://example.org/s", Set.of()),
                new IRITerm("http://example.org/p", Set.of()),
                new LiteralTerm("value", new IRITerm("http://www.w3.org/2001/XMLSchema#string", Set.of()), null, Set.of())
        );

        String serialized = NQuadsWriter.serializeStatement(statement);

        assertEquals(
                "<http://example.org/s> <http://example.org/p> \"value\" .",
                serialized
        );
    }

    @Test
    public void keepsPlainLiteralSerializationUnchanged() {
        RdfStatement statement = new RdfStatement(
                new IRITerm("http://example.org/s", Set.of()),
                new IRITerm("http://example.org/p", Set.of()),
                new LiteralTerm("value", null, null, Set.of())
        );

        String serialized = NQuadsWriter.serializeStatement(statement);

        assertEquals(
                "<http://example.org/s> <http://example.org/p> \"value\" .",
                serialized
        );
    }
}
