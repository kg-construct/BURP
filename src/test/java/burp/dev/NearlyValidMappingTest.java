package burp.dev;

import burp.dev.rml.RmlDevServerImpl;
import burp.dev.rml.service.validator.RmlDiagnostic;
import burp.dev.rml.service.validator.ValidateParams;
import burp.model.*;
import burp.parse.RmlLoader;
import burp.parse.ValidationMode;
import burp.parse.turtleprov.ProvStore;
import burp.parse.turtleprov.TurtleProvParser;
import burp.vocabularies.BURP;
import burp.vocabularies.Rml;
import org.apache.jena.riot.Lang;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class NearlyValidMappingTest {

    private static final String DIR = "src/test/resources/burp/nearly_valid/";

    private static String loadFile(String name) {
        try {
            return Files.readString(Path.of(DIR, name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final String ONTOLOGY_TTL = loadFile("ontology.ttl");
    private static final String INCOMPLETE_PREDICATE_TTL = loadFile("incomplete_predicate.ttl");
    private static final String INCOMPLETE_PREDICATE_MAP_TTL = loadFile("incomplete_constant.ttl");
    private static final String INCOMPLETE_DATATYPE_TTL = loadFile("incomplete_datatype.ttl");
    private static final String INCOMPLETE_CLASS_TTL = loadFile("incomplete_class.ttl");
    private static final String INCOMPLETE_FUNCTION_TTL = loadFile("incomplete_function.ttl");
    private static final String INCOMPLETE_PARAMETER_TTL = loadFile("incomplete_parameter.ttl");
    private static final String PARENT_TRIPLES_MAP_TTL = loadFile("parent_triples_map.ttl");

    private List<CompletionItem> complete(RmlDevServerImpl server, String uri, Position pos) throws Exception {
        var either = server.getTextDocumentService().completion(new CompletionParams(new TextDocumentIdentifier(uri), pos)).get();
        if (either == null) return List.of();
        return either.isLeft() ? either.getLeft() : either.getRight().getItems();
    }

    private Position findCursor(String text) {
        String[] lines = text.split("\r?\n");
        for (int i = 0; i < lines.length; i++) {
            int idx = lines[i].indexOf("#cursor");
            if (idx >= 0) {
                return new Position(i, idx);
            }
        }
        throw new IllegalArgumentException("#cursor not found");
    }

    // --- Case 1: Incomplete predicate in predicateObjectMap ---

    @Test
    public void testIncompletePredicate_RdfRecovery() {
        ProvStore store = TurtleProvParser.parseTurtleFromString(INCOMPLETE_PREDICATE_TTL);
        assertThat(store.getTriples())
                .anyMatch(t -> Rml.predicateObjectMap.equals(t.statement().getPredicate().getURI()))
                .anyMatch(t -> Rml.subjectMap.equals(t.statement().getPredicate().getURI()))
                .anyMatch(t -> Rml.predicate.equals(t.statement().getPredicate().getURI())
                        && t.statement().getObject().isResource()
                        && BURP.hole.getURI().equals(t.statement().getObject().asResource().getURI()));
    }

    @Test
    public void testIncompletePredicate_AstLoading() {
        RmlLoader loader = new RmlLoader(INCOMPLETE_PREDICATE_TTL).withValidationMode(ValidationMode.LENIENT);
        MappingDocument doc = loader.loadMappingDocument();
        assertThat(doc.triplesMaps).hasSize(1);

        TriplesMap tm = doc.triplesMaps.getFirst();
        assertThat(tm.subjectMap).isNotNull();
        assertThat(tm.subjectMap.classes).extracting(r -> r.getURI()).contains("http://example.org/Patient");

        assertThat(tm.predicateObjectMaps).hasSize(1);
        PredicateObjectMap pom = tm.predicateObjectMaps.getFirst();
        assertThat(pom.objectMaps).hasSize(1);
        assertThat(pom.predicateMaps).hasSize(1);
        assertThat(pom.predicateMaps.getFirst().expression).isNull();
    }

    @Test
    public void testIncompletePredicate_Completion() throws Exception {
        String uri = "file:///test-incomplete-pred.ttl";
        RmlDevServerImpl server = new RmlDevServerImpl();
        server.getWorkspace().updateDocumentText(uri, INCOMPLETE_PREDICATE_TTL);
        server.getWorkspace().getOntologyRegistry(null, uri).loadOntology(ONTOLOGY_TTL, Lang.TURTLE);

        Position pos = findCursor(INCOMPLETE_PREDICATE_TTL);
        List<CompletionItem> completions = complete(server, uri, pos);
        assertThat(completions).isNotEmpty();

        List<String> labels = completions.stream().map(CompletionItem::getLabel).toList();
        assertThat(labels).contains("ex:treatedBy", "ex:patientId", "ex:name");
    }

    // --- Case 2: Incomplete constant in predicateMap ---

    @Test
    public void testIncompletePredicateMapConstant_RdfRecovery() {
        ProvStore store = TurtleProvParser.parseTurtleFromString(INCOMPLETE_PREDICATE_MAP_TTL);
        assertThat(store.getTriples())
                .anyMatch(t -> Rml.constant.equals(t.statement().getPredicate().getURI())
                        && t.statement().getObject().isResource()
                        && BURP.hole.getURI().equals(t.statement().getObject().asResource().getURI()));
    }

    @Test
    public void testIncompletePredicateMapConstant_AstLoading() {
        RmlLoader loader = new RmlLoader(INCOMPLETE_PREDICATE_MAP_TTL).withValidationMode(ValidationMode.LENIENT);
        MappingDocument doc = loader.loadMappingDocument();
        assertThat(doc.triplesMaps).hasSize(1);

        PredicateObjectMap pom = doc.triplesMaps.getFirst().predicateObjectMaps.getFirst();
        assertThat(pom.predicateMaps).hasSize(1);
        assertThat(pom.predicateMaps.getFirst().expression).isNull();
    }

    @Test
    public void testIncompletePredicateMapConstant_Completion() throws Exception {
        String uri = "file:///test-incomplete-pm-const.ttl";
        RmlDevServerImpl server = new RmlDevServerImpl();
        server.getWorkspace().updateDocumentText(uri, INCOMPLETE_PREDICATE_MAP_TTL);
        server.getWorkspace().getOntologyRegistry(null, uri).loadOntology(ONTOLOGY_TTL, Lang.TURTLE);

        Position pos = findCursor(INCOMPLETE_PREDICATE_MAP_TTL);
        List<CompletionItem> completions = complete(server, uri, pos);
        assertThat(completions).isNotEmpty();

        List<String> labels = completions.stream().map(CompletionItem::getLabel).toList();
        assertThat(labels).contains("ex:treatedBy", "ex:patientId", "ex:name");
    }

    // --- Case 3: Incomplete datatype in objectMap ---

    @Test
    public void testIncompleteDatatype_RdfRecovery() {
        ProvStore store = TurtleProvParser.parseTurtleFromString(INCOMPLETE_DATATYPE_TTL);
        assertThat(store.getTriples())
                .anyMatch(t -> Rml.datatype.equals(t.statement().getPredicate().getURI())
                        && t.statement().getObject().isResource()
                        && BURP.hole.getURI().equals(t.statement().getObject().asResource().getURI()));
    }

    @Test
    public void testIncompleteDatatype_AstLoading() {
        RmlLoader loader = new RmlLoader(INCOMPLETE_DATATYPE_TTL).withValidationMode(ValidationMode.LENIENT);
        MappingDocument doc = loader.loadMappingDocument();
        assertThat(doc.triplesMaps).hasSize(1);

        BaseObjectMap om = doc.triplesMaps.getFirst().predicateObjectMaps.getFirst().objectMaps.getFirst();
        assertThat(om).isInstanceOf(TermMap.class);
        TermMap tm = (TermMap) om;
        assertThat(tm.datatypeMap).isNotNull();
        assertThat(tm.datatypeMap.expression).isNull();
    }

    @Test
    public void testIncompleteDatatype_Completion() throws Exception {
        String uri = "file:///test-incomplete-dt.ttl";
        RmlDevServerImpl server = new RmlDevServerImpl();
        server.getWorkspace().updateDocumentText(uri, INCOMPLETE_DATATYPE_TTL);
        server.getWorkspace().getOntologyRegistry(null, uri).loadOntology(ONTOLOGY_TTL, Lang.TURTLE);

        Position pos = findCursor(INCOMPLETE_DATATYPE_TTL);
        List<CompletionItem> completions = complete(server, uri, pos);
        assertThat(completions).isNotEmpty();

        List<String> labels = completions.stream().map(CompletionItem::getLabel).toList();
        assertThat(labels).contains("xsd:integer");
    }

    // --- Case 4: Incomplete class on subjectMap ---

    @Test
    public void testIncompleteClass_RdfRecovery() {
        ProvStore store = TurtleProvParser.parseTurtleFromString(INCOMPLETE_CLASS_TTL);
        assertThat(store.getTriples())
                .anyMatch(t -> Rml.class_.equals(t.statement().getPredicate().getURI())
                        && t.statement().getObject().isResource()
                        && BURP.hole.getURI().equals(t.statement().getObject().asResource().getURI()));
    }

    @Test
    public void testIncompleteClass_AstLoading() {
        RmlLoader loader = new RmlLoader(INCOMPLETE_CLASS_TTL).withValidationMode(ValidationMode.LENIENT);
        MappingDocument doc = loader.loadMappingDocument();
        assertThat(doc.triplesMaps).hasSize(1);

        SubjectMap sm = doc.triplesMaps.getFirst().subjectMap;
        assertThat(sm).isNotNull();
        // BURP.hole was ignored and not added to classes list
        assertThat(sm.classes).isEmpty();
    }

    @Test
    public void testIncompleteClass_Completion() throws Exception {
        String uri = "file:///test-incomplete-class.ttl";
        RmlDevServerImpl server = new RmlDevServerImpl();
        server.getWorkspace().updateDocumentText(uri, INCOMPLETE_CLASS_TTL);
        server.getWorkspace().getOntologyRegistry(null, uri).loadOntology(ONTOLOGY_TTL, Lang.TURTLE);

        Position pos = findCursor(INCOMPLETE_CLASS_TTL);
        List<CompletionItem> completions = complete(server, uri, pos);
        assertThat(completions).isNotEmpty();

        List<String> labels = completions.stream().map(CompletionItem::getLabel).toList();
        assertThat(labels).contains("ex:Patient", "ex:Doctor", "ex:Person");
    }

    // --- Case 5: Incomplete function in functionExecution ---

    @Test
    public void testIncompleteFunction_RdfRecovery() {
        ProvStore store = TurtleProvParser.parseTurtleFromString(INCOMPLETE_FUNCTION_TTL);
        assertThat(store.getTriples())
                .anyMatch(t -> Rml.function.equals(t.statement().getPredicate().getURI())
                        && t.statement().getObject().isResource()
                        && BURP.hole.getURI().equals(t.statement().getObject().asResource().getURI()));
    }

    @Test
    public void testIncompleteFunction_Completion() throws Exception {
        String uri = "file:///test-incomplete-fn.ttl";
        RmlDevServerImpl server = new RmlDevServerImpl();
        server.getWorkspace().updateDocumentText(uri, INCOMPLETE_FUNCTION_TTL);

        Position pos = findCursor(INCOMPLETE_FUNCTION_TTL);
        List<CompletionItem> completions = complete(server, uri, pos);
        assertThat(completions).isNotEmpty();

        List<String> labels = completions.stream().map(CompletionItem::getLabel).toList();
        assertThat(labels).contains("grel:toUpperCase", "grel:toLowerCase");
    }

    // --- Case 6: Incomplete parameter in input ---

    @Test
    public void testIncompleteParameter_RdfRecovery() {
        ProvStore store = TurtleProvParser.parseTurtleFromString(INCOMPLETE_PARAMETER_TTL);
        assertThat(store.getTriples())
                .anyMatch(t -> Rml.parameter.equals(t.statement().getPredicate().getURI())
                        && t.statement().getObject().isResource()
                        && BURP.hole.getURI().equals(t.statement().getObject().asResource().getURI()));
    }

    @Test
    public void testIncompleteParameter_Completion() throws Exception {
        String uri = "file:///test-incomplete-param.ttl";
        RmlDevServerImpl server = new RmlDevServerImpl();
        server.getWorkspace().updateDocumentText(uri, INCOMPLETE_PARAMETER_TTL);

        Position pos = findCursor(INCOMPLETE_PARAMETER_TTL);
        List<CompletionItem> completions = complete(server, uri, pos);
        assertThat(completions).isNotEmpty();

        List<String> labels = completions.stream().map(CompletionItem::getLabel).toList();
        assertThat(labels).contains("grel:valueParam");
    }

    // --- Case 7: ParentTriplesMap completion does not propose predicates ---

    @Test
    public void testParentTriplesMap_Completion_DoesNotProposePredicates() throws Exception {
        String uri = "file:///test-parent-tm.ttl";
        RmlDevServerImpl server = new RmlDevServerImpl();
        server.getWorkspace().updateDocumentText(uri, PARENT_TRIPLES_MAP_TTL);
        server.getWorkspace().getOntologyRegistry(null, uri).loadOntology(ONTOLOGY_TTL, org.apache.jena.riot.Lang.TURTLE);

        Position pos = findCursor(PARENT_TRIPLES_MAP_TTL);
        List<CompletionItem> completions = complete(server, uri, pos);
        assertThat(completions).isNotEmpty();

        List<String> labels = completions.stream().map(CompletionItem::getLabel).toList();
        assertThat(labels).contains("ex:DoctorMapping");
        assertThat(labels).doesNotContain("ex:worksAt", "ex:treatedBy", "ex:name", "ex:patientId");
    }

    // --- Case 8: Real mapping with owl:imports, CSV sources, static & dynamic validation ---

    @Test
    public void testOntologyPlayground_StaticAndDynamicValidation() throws Exception {
        Path mappingFile = Path.of("src/test/resources/burp/nearly_valid/ontology_playground.ttl");
        RmlDevServerImpl server = new RmlDevServerImpl();
        server.getWorkspace().setCurrentWorkingDirectory(Path.of("src/test/resources/burp/nearly_valid"));

        String uri = mappingFile.toUri().toString();
        var loaded = server.getWorkspace().loadMappingDetailed(uri, ValidationMode.LENIENT);
        var registry = server.getWorkspace().getOntologyRegistry(loaded.model(), uri);
        var prefixMap = server.getWorkspace().getPrefixMap(uri);

        // 1. Static and Dynamic validation (server.validate runs both immediate and async validators)
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(uri)).get();

        // Static checks:
        assertThat(diagnostics)
                .filteredOn(d -> burp.vocabularies.RER.OntologyDatatypeMismatch.getURI().equals(d.rerCodeUri()))
                .isNotEmpty(); // ex:name declared as xsd:integer on string range property

        assertThat(diagnostics)
                .filteredOn(d -> burp.vocabularies.RER.OntologyDomainMismatch.getURI().equals(d.rerCodeUri()))
                .isNotEmpty(); // ex:treatedBy declared on ex:DoctorMapping (domain is ex:Patient)

        // Dynamic checks from sampled source data:
        assertThat(diagnostics)
                .extracting(RmlDiagnostic::message)
                .anyMatch(msg -> msg.contains("[row 2]") && msg.contains("'12/04/1995'") && msg.contains("xsd:date"))
                .anyMatch(msg -> msg.contains("[row 2]") && msg.contains("'-5'") && msg.contains("xsd:nonNegativeInteger"))
                .anyMatch(msg -> msg.contains("[row 3]") && msg.contains("'not_a_number'") && msg.contains("xsd:nonNegativeInteger"));

        // 2. Dynamic evaluation: checks runtime generated terms against ontology
        burp.model.TriplesMap doctorTm = loaded.document().triplesMaps.stream()
                .filter(tm -> tm.getIri() != null && tm.getIri().toString().contains("DoctorMapping"))
                .findFirst()
                .orElseThrow();

        var evalFuture = server.evaluate(new burp.dev.rml.service.eval.EvaluateParams(
                new burp.dev.rml.service.selector.IdSelector(uri, doctorTm.getId()),
                new burp.dev.rml.service.eval.AllDataContext()
        ));
        var evalResults = evalFuture.get();
        assertThat(evalResults).isNotEmpty();
        assertThat(evalResults.getFirst().diagnostics())
                .anyMatch(msg -> msg.contains("ex:treatedBy") && msg.contains("ex:Doctor"));

        // 3. Dynamic evaluation on PatientMapping: checks runtime data-driven violations
        burp.model.TriplesMap patientTm = loaded.document().triplesMaps.stream()
                .filter(tm -> tm.getIri() != null && tm.getIri().toString().contains("PatientMapping"))
                .findFirst()
                .orElseThrow();

        var patientEvalResults = server.evaluate(new burp.dev.rml.service.eval.EvaluateParams(
                new burp.dev.rml.service.selector.IdSelector(uri, patientTm.getId()),
                new burp.dev.rml.service.eval.AllDataContext()
        )).get();

        assertThat(patientEvalResults).hasSize(3);

        // Iteration 1 (Alice): valid Age 30, valid BirthDate 1993-04-12, but ex:treatedBy receives literal "101"
        assertThat(patientEvalResults.get(0).diagnostics())
                .anyMatch(msg -> msg.contains("ex:treatedBy") && msg.contains("object property") && msg.contains("Literal"));

        // Iteration 2 (Bob): invalid negative age -5 for xsd:nonNegativeInteger, invalid date format 12/04/1995 for xsd:date
        assertThat(patientEvalResults.get(1).diagnostics())
                .anyMatch(msg -> msg.contains("'-5'") && msg.contains("xsd:nonNegativeInteger"))
                .anyMatch(msg -> msg.contains("'12/04/1995'") && msg.contains("xsd:date"));

        // Iteration 3 (Charlie): invalid lexical integer "not_a_number"
        assertThat(patientEvalResults.get(2).diagnostics())
                .anyMatch(msg -> msg.contains("'not_a_number'") && msg.contains("xsd:nonNegativeInteger"));
    }
}
