package burp;

import burp.parse.Parse;
import burp.reporting.StatementParts;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.validation.ReportEntry;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParseShaclViolationTest {

    @Test
    public void testExtractStatementsFromShaclViolation() {
        String shapesGraph = """
                    @prefix sh: <http://www.w3.org/ns/shacl#> .
                    @prefix ex: <http://example.org/> .
                    @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                
                    ex:PersonShape
                        a sh:NodeShape ;
                        sh:targetClass ex:Person ;
                        sh:property [
                            sh:path ex:age ;
                            sh:datatype xsd:integer ;
                        ] .
                """;

        String dataGraph = """
                    @prefix ex: <http://example.org/> .
                
                    ex:Alice a ex:Person ;
                        ex:age "twenty" .
                """;

        Model shapesModel = ModelFactory.createDefaultModel();
        shapesModel.read(new ByteArrayInputStream(shapesGraph.getBytes(StandardCharsets.UTF_8)), null, "TURTLE");

        Model dataModel = ModelFactory.createDefaultModel();
        dataModel.read(new ByteArrayInputStream(dataGraph.getBytes(StandardCharsets.UTF_8)), null, "TURTLE");

        ValidationReport report = ShaclValidator.get().validate(shapesModel.getGraph(), dataModel.getGraph());
        Collection<ReportEntry> entries = report.getEntries();

        assertEquals(1, entries.size(), "Should have 1 violation");
        ReportEntry vr = entries.iterator().next();

        Parse parse = new Parse();
        List<StatementParts> statements = parse.extractStatementsFromShaclViolation(vr, dataModel);

        assertEquals(1, statements.size(), "Should extract 1 relevant statement");
        Statement stmt = statements.getFirst().stmt();
        assertEquals("http://example.org/Alice", stmt.getSubject().getURI());
        assertEquals("http://example.org/age", stmt.getPredicate().getURI());
        assertEquals("twenty", stmt.getObject().asLiteral().getLexicalForm());
    }

    @Test
    public void testExtractStatementsFromShaclViolation_minCount() {
        String shapesGraph = """
                    @prefix sh: <http://www.w3.org/ns/shacl#> .
                    @prefix ex: <http://example.org/> .
                
                    ex:PersonShape
                        a sh:NodeShape ;
                        sh:targetClass ex:Person ;
                        sh:property [
                            sh:path ex:name ;
                            sh:minCount 1 ;
                        ] .
                """;

        String dataGraph = """
                    @prefix ex: <http://example.org/> .
                    ex:Bob a ex:Person .
                """;

        Model shapesModel = ModelFactory.createDefaultModel();
        shapesModel.read(new ByteArrayInputStream(shapesGraph.getBytes(StandardCharsets.UTF_8)), null, "TURTLE");

        Model dataModel = ModelFactory.createDefaultModel();
        dataModel.read(new ByteArrayInputStream(dataGraph.getBytes(StandardCharsets.UTF_8)), null, "TURTLE");

        ValidationReport report = ShaclValidator.get().validate(shapesModel.getGraph(), dataModel.getGraph());
        Collection<ReportEntry> entries = report.getEntries();

        assertEquals(1, entries.size(), "Should have 1 violation");
        ReportEntry vr = entries.iterator().next();

        Parse parse = new Parse();
        List<StatementParts> statements = parse.extractStatementsFromShaclViolation(vr, dataModel);

        // In minCount, it looks for the statements with ex:name, but Bob has none.
        // It should fallback to retrieving statements where Bob is involved.
        assertEquals(1, statements.size(), "Should fallback to focus nodes statement");
        Statement stmt = statements.getFirst().stmt();
        assertEquals("http://example.org/Bob", stmt.getSubject().getURI());
        assertEquals(RDF.type.getURI(), stmt.getPredicate().getURI());
        assertEquals("http://example.org/Person", stmt.getObject().asResource().getURI());
    }
}
