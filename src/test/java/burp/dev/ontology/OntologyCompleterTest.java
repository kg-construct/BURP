package burp.dev.ontology;

import burp.dev.rml.service.completion.ontology.OntologyCompleter;
import burp.dev.rml.service.ontology.OntologyRegistry;
import burp.model.*;
import burp.model.rdf.IRITerm;
import org.apache.jena.rdf.model.ResourceFactory;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class OntologyCompleterTest {

    private static final Path ONTOLOGY_PATH = Path.of("src/test/resources/burp/ontology/completer_ontology.ttl");

    private OntologyRegistry registry;
    private OntologyCompleter completer;
    private final Map<String, String> prefixMap = Map.of("ex", "http://example.org/", "xsd", "http://www.w3.org/2001/XMLSchema#");

    @BeforeEach
    public void setUp() throws Exception {
        registry = new OntologyRegistry();
        registry.loadOntology(ONTOLOGY_PATH);
        completer = new OntologyCompleter();
    }

    @Test
    public void testPredicateCompletionOrderingAndInheritance() {
        TriplesMap tm = new TriplesMap();
        tm.subjectMap = new SubjectMap();
        tm.subjectMap.classes.add(ResourceFactory.createResource("http://example.org/Patient"));

        // Add ex:patientId as already present in the TriplesMap
        PredicateObjectMap existingPom = new PredicateObjectMap();
        PredicateMap pm = new PredicateMap();
        pm.expression = new RDFNodeConstant(new IRITerm("http://example.org/patientId"));
        existingPom.predicateMaps.add(pm);
        tm.predicateObjectMaps.add(existingPom);

        List<CompletionItem> items = completer.completePredicates(
                tm,
                registry,
                prefixMap,
                "", // No filter
                null
        );

        assertThat(items).isNotEmpty();

        // Direct non-present properties first: ex:treatedBy (distance 0)
        // Parent properties next: ex:name (distance 1), then ex:dnaSequence (distance 2)
        // Already present at bottom: ex:patientId
        List<String> labels = items.stream().map(CompletionItem::getLabel).toList();

        int treatedByIdx = labels.indexOf("ex:treatedBy");
        int nameIdx = labels.indexOf("ex:name");
        int dnaIdx = labels.indexOf("ex:dnaSequence");
        int patientIdIdx = labels.indexOf("ex:patientId");

        assertThat(treatedByIdx).isLessThan(nameIdx);
        assertThat(nameIdx).isLessThan(dnaIdx);
        assertThat(dnaIdx).isLessThan(patientIdIdx); // Already present is pushed after others
    }

    @Test
    public void testFuzzyFilteringMatchesAndScores() {
        TriplesMap tm = new TriplesMap();
        tm.subjectMap = new SubjectMap();
        tm.subjectMap.classes.add(ResourceFactory.createResource("http://example.org/Patient"));

        // User typed "pId" which matches ex:patientId via subsequence
        List<CompletionItem> items = completer.completePredicates(
                tm,
                registry,
                prefixMap,
                "pId",
                null
        );

        assertThat(items)
                .extracting(CompletionItem::getLabel)
                .contains("ex:patientId")
                .doesNotContain("ex:name", "ex:treatedBy");
    }

    @Test
    public void testClassCompletion() {
        List<CompletionItem> classes = completer.completeClasses(
                registry,
                prefixMap,
                "Pat",
                null
        );

        assertThat(classes)
                .extracting(CompletionItem::getLabel)
                .contains("ex:Patient")
                .doesNotContain("ex:Doctor", "ex:LivingBeing");

        assertThat(classes.getFirst().getKind()).isEqualTo(CompletionItemKind.Class);
    }

    @Test
    public void testDatatypeCompletion() {
        var pred = ResourceFactory.createResource("http://example.org/patientId");
        List<CompletionItem> dts = completer.completeDatatypes(
                pred,
                registry,
                prefixMap,
                "",
                null
        );

        assertThat(dts)
                .extracting(CompletionItem::getLabel)
                .containsExactly("xsd:integer");
        assertThat(dts.getFirst().getSortText()).isEqualTo("0000");

        // Non-matching prefix: still proposed, but with lower priority sortText ("9999")
        List<CompletionItem> nonMatching = completer.completeDatatypes(
                pred,
                registry,
                prefixMap,
                "xsd:str",
                null
        );
        assertThat(nonMatching)
                .extracting(CompletionItem::getLabel)
                .containsExactly("xsd:integer");
        assertThat(nonMatching.getFirst().getSortText()).isEqualTo("9999");
    }
}
