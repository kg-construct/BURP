package burp.dev;

import burp.dev.rml.RmlDevServerImpl;
import burp.dev.rml.RmlLanguageServer;
import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.eval.*;
import burp.dev.rml.service.validator.RmlDiagnostic;
import burp.dev.rml.service.validator.ValidateParams;
import burp.model.RawValue;
import burp.model.rdf.IRITerm;
import burp.model.rdf.LiteralTerm;
import burp.vocabularies.RER;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.eclipse.lsp4j.*;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class InlineEvaluateTest {

    @Test
    public void testInlineEvaluateConcatMapping() throws ExecutionException, InterruptedException {
        InlineEvaluateParams params = new InlineEvaluateParams(
                "src/test/resources/burp/BURPFNMLTC-CSV-Concat/mapping.ttl",
                new AllDataContext()
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        List<InlineEvaluateItem> items = server.inlineEvaluate(params).get();

        assertThat(items).isNotNull().isNotEmpty();

        // Raw evaluated values exist
        assertThat(items).anyMatch(i -> i.values().stream().anyMatch(v -> v instanceof LiteralTerm lt && lt.value().equals("1")));
        assertThat(items).anyMatch(i -> i.values().stream().anyMatch(v -> v instanceof LiteralTerm lt && lt.value().equals("Gaston")));
        assertThat(items).anyMatch(i -> i.values().stream().anyMatch(v -> v instanceof LiteralTerm lt && lt.value().equals("A")));
        assertThat(items).anyMatch(i -> i.values().stream().anyMatch(v -> v instanceof LiteralTerm lt && lt.value().equals("Gaston - A")));

        // Pre-formatted textual values exist
        assertThat(items).anyMatch(i -> i.formattedValues().stream().anyMatch(v -> v.contains("\"1\"") || v.equals("1")));
        assertThat(items).anyMatch(i -> i.formattedValues().stream().anyMatch(v -> v.contains("\"Gaston\"") || v.equals("Gaston")));
        assertThat(items).anyMatch(i -> i.formattedValues().stream().anyMatch(v -> v.contains("\"A\"") || v.equals("A")));
        assertThat(items).anyMatch(i -> i.formattedValues().stream().anyMatch(v -> v.contains("\"Gaston - A\"") || v.equals("Gaston - A")));
        assertThat(items).anyMatch(i -> i.formattedValues().stream().anyMatch(v -> v.contains("http://example.com/1")));


        // Standard LSP Inlay Hints endpoint test
        InlayHintParams hintParams = new InlayHintParams(
                new TextDocumentIdentifier("src/test/resources/burp/BURPFNMLTC-CSV-Concat/mapping.ttl"),
                new Range(new Position(0, 0), new Position(100, 0))
        );
        List<InlayHint> hints = server.getTextDocumentService().inlayHint(hintParams).get();
        assertThat(hints).isNotNull().isNotEmpty();

        List<String> hintLabels = hints.stream().map(h -> h.getLabel().getLeft()).toList();
        assertThat(hintLabels).anyMatch(l -> l.contains("1") || l.contains("\"1\""));
        assertThat(hintLabels).anyMatch(l -> l.contains("http://example.com/1"));
        assertThat(hintLabels).anyMatch(l -> l.contains("Gaston"));
        assertThat(hintLabels).anyMatch(l -> l.contains("Gaston - A"));
        assertThat(hintLabels).anyMatch(l -> l.contains("input string (xsd:string)"));
    }

    @Test
    public void templateReferenceInlayHints_useRawTurtleSourcePositions() throws Exception {
        String uri = "src/test/resources/burp/eval_alice/mapping.ttl";
        String unicodeEscape = "\\" + "u0065";
        String mapping = """
                @prefix ex: <http://example.org/> .
                @prefix rml: <http://w3id.org/rml/> .

                ex:PersonMapping a rml:TriplesMap ;
                    rml:logicalSource [ a rml:LogicalSource ;
                        rml:referenceFormulation rml:JSONPath ;
                        rml:iterator "$[*]" ;
                        rml:source [ a rml:RelativePathSource ; rml:root rml:MappingDirectory ; rml:path "data.json" ]
                    ] ;
                    rml:subjectMap [ rml:template "plain/{$.name}" ] ;
                    rml:predicateObjectMap [
                        rml:predicate ex:unicode ;
                        rml:objectMap [ rml:template "prefix %s/{$.name}" ]
                    ], [
                        rml:predicate ex:escapedBraces ;
                        rml:objectMap [ rml:template "prefix \\\\{literal\\\\}/{$.name}" ]
                    ] .
                """.formatted(unicodeEscape);

        RmlDevServerImpl server = new RmlDevServerImpl();
        server.getWorkspace().updateDocumentText(uri, mapping);

        List<InlayHint> hints = server.getTextDocumentService().inlayHint(new InlayHintParams(
                new TextDocumentIdentifier(uri),
                new Range(new Position(0, 0), new Position(100, 0))
        )).get();

        List<String> mappingLines = mapping.lines().toList();
        List<Position> expectedPositions = IntStream.range(0, mappingLines.size())
                .mapToObj(lineNumber -> new Position(lineNumber, referenceEndColumn(mappingLines.get(lineNumber))))
                .filter(position -> position.getCharacter() >= 0)
                .toList();

        assertThat(hints)
                .filteredOn(hint -> " ⇾ \"Alice\"".equals(hint.getLabel().getLeft()))
                .as("Reference hints must be placed after the reference, before its closing brace, in raw Turtle source")
                .extracting(InlayHint::getPosition)
                .containsExactlyInAnyOrderElementsOf(expectedPositions);
    }

    private static int referenceEndColumn(String line) {
        int referenceStart = line.indexOf("{$.name}");
        return referenceStart < 0 ? -1 : referenceStart + "{$.name".length();
    }

    @Test
    public void disabledInlineHintEvaluation_keepsStaticFunctionAndJoinHints() throws Exception {
        RmlDevServerImpl server = new RmlDevServerImpl();
        server.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams(
                Map.of("rml", Map.of("inlineHints", Map.of("enableEvaluation", false)))
        ));

        List<InlayHint> functionHints = server.getTextDocumentService().inlayHint(new InlayHintParams(
                new TextDocumentIdentifier("src/test/resources/burp/BURPFNMLTC-CSV-Concat/mapping.ttl"),
                new Range(new Position(0, 0), new Position(100, 0))
        )).get();
        List<String> functionLabels = functionHints.stream().map(h -> h.getLabel().getLeft()).toList();
        assertThat(functionLabels).anyMatch(label -> label.contains("input string (xsd:string)"));
        assertThat(functionLabels).noneMatch(label -> label.contains("Gaston") || label.contains("http://example.com/1"));

        List<InlayHint> joinHints = server.getTextDocumentService().inlayHint(new InlayHintParams(
                new TextDocumentIdentifier("rml-modules/rml-lv/test-cases/RMLLVTC0006a/mapping.ttl"),
                new Range(new Position(0, 0), new Position(100, 0))
        )).get();
        List<String> joinLabels = joinHints.stream().map(h -> h.getLabel().getLeft()).toList();
        assertThat(joinLabels).anyMatch(label -> label.contains("people.csv"));
        assertThat(joinLabels).anyMatch(label -> label.contains("people.json"));
        assertThat(joinLabels).noneMatch(label -> label.contains("person/alice") || label.contains("1995"));
    }

    @Test
    public void testInlineEvaluateWithRangeFilter() throws ExecutionException, InterruptedException {
        String uri = "src/test/resources/burp/BURPFNMLTC-CSV-Concat/mapping.ttl";
        // Range covering only the subjectMap (line 11), excluding predicateObjectMap and FnO execution
        Range filterRange = new Range(new Position(10, 0), new Position(12, 0));
        InlineEvaluateParams params = new InlineEvaluateParams(
                uri,
                new AllDataContext(),
                filterRange
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        List<InlineEvaluateItem> items = server.inlineEvaluate(params).get();

        assertThat(items).isNotNull().isNotEmpty();
        // SubjectMap at line 11 is included
        assertThat(items).anyMatch(i -> i.values().stream().anyMatch(v -> v instanceof LiteralTerm lt && lt.value().equals("1")));
        // Execution at line 14 and reference at line 23 are excluded
        assertThat(items).noneMatch(i -> i.values().stream().anyMatch(v -> v instanceof LiteralTerm lt && lt.value().equals("Gaston")));
        assertThat(items).noneMatch(i -> i.values().stream().anyMatch(v -> v instanceof LiteralTerm lt && lt.value().equals("Gaston - A")));
    }

    @Test
    public void testInlineEvaluatePrefixCompaction() throws ExecutionException, InterruptedException {
        InlineEvaluateParams params = new InlineEvaluateParams(
                "src/test/resources/burp/eval_alice/mapping.ttl",
                new AllDataContext()
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        List<InlineEvaluateItem> items = server.inlineEvaluate(params).get();

        assertThat(items).isNotNull().isNotEmpty();

        // Check 1: Raw values for ReferenceSegment and Template
        assertThat(items).anyMatch(i -> i.values().stream().anyMatch(v -> v instanceof LiteralTerm lt && lt.value().equals("Alice")));
        assertThat(items).anyMatch(i -> i.values().stream().anyMatch(v -> v instanceof LiteralTerm lt && lt.value().equals("30")));
        assertThat(items).anyMatch(i -> i.values().stream().anyMatch(v -> v instanceof IRITerm iri && iri.uri().equals("http://example.org/person/Alice")));

        // Check 2: Standard LSP Inlay Hints with prefix compaction
        InlayHintParams hintParams = new InlayHintParams(
                new TextDocumentIdentifier("src/test/resources/burp/eval_alice/mapping.ttl"),
                new Range(new Position(0, 0), new Position(100, 0))
        );
        List<InlayHint> hints = server.getTextDocumentService().inlayHint(hintParams).get();
        List<String> hintLabels = hints.stream().map(h -> h.getLabel().getLeft()).toList();

        assertThat(hintLabels).anyMatch(l -> l.contains("Alice"));
        assertThat(hintLabels).anyMatch(l -> l.contains("30"));
        assertThat(hintLabels).anyMatch(l -> l.contains("ex:person/Alice"));
    }

    @Test
    public void testInlineEvaluateCustomDataContext() throws ExecutionException, InterruptedException {
        Map<String, Object> customValues = new HashMap<>();
        customValues.put("Id", "10");
        customValues.put("Name", "Venus");
        customValues.put("Class", "Pro");

        InlineEvaluateParams params = new InlineEvaluateParams(
                "src/test/resources/burp/BURPFNMLTC-CSV-Concat/mapping.ttl",
                new ReferenceValuesCustomDataContext(customValues)
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        List<InlineEvaluateItem> items = server.inlineEvaluate(params).get();

        assertThat(items).isNotNull().isNotEmpty();

        assertThat(items).anyMatch(i -> i.values().stream().anyMatch(v -> v instanceof LiteralTerm lt && lt.value().equals("Venus")));
        assertThat(items).anyMatch(i -> i.values().stream().anyMatch(v -> v instanceof LiteralTerm lt && lt.value().equals("Pro")));
        assertThat(items).anyMatch(i -> i.values().stream().anyMatch(v -> v instanceof IRITerm iri && iri.uri().equals("http://example.com/10")));
        assertThat(items).anyMatch(i -> i.values().stream().anyMatch(v -> v instanceof LiteralTerm lt && lt.value().equals("Venus - Pro")));
    }

    @Test
    public void testInlineEvaluateConditionsFunctionsAndCollections() throws ExecutionException, InterruptedException {
        InlineEvaluateParams params = new InlineEvaluateParams(
                "src/test/resources/burp/BURP-FNML+CC-02/mapping.ttl",
                new AllDataContext()
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        List<InlineEvaluateItem> items = server.inlineEvaluate(params).get();

        assertThat(items).isNotNull().isNotEmpty();

        // 1. Template: ex:student/1
        assertThat(items).anyMatch(i -> i.values().stream().anyMatch(v -> v instanceof IRITerm iri && iri.uri().equals("http://example.com/student/1")));

        // 2. Condition: boolean true
        assertThat(items).anyMatch(i -> i.type().equals("Condition") && i.values().stream().anyMatch(v -> v instanceof RawValue rv && rv.value().toString().equalsIgnoreCase("true")));

        // 3. Input parameter reference "role": "admin"
        assertThat(items).anyMatch(i -> i.values().stream().anyMatch(v -> v instanceof LiteralTerm lt && lt.value().equals("admin")));

        // 4. GatherMap collection exists
        assertThat(items).anyMatch(i -> i.values().stream().anyMatch(v -> v instanceof burp.model.rdf.RdfListTerm));

        // Verify Gson serialization works without exception for all items (including RawValue and RdfListTerm)
        com.google.gson.GsonBuilder builder = new com.google.gson.GsonBuilder();
        burp.dev.DevServer.configureGson(builder);
        com.google.gson.Gson gson = builder.create();
        String json = gson.toJson(items);
        assertThat(json).isNotNull().contains("Condition");
        assertThat(json).contains("\"type\":\"raw\"");

        // 5. Verify Standard Inlay Hints formatting
        InlayHintParams hintParams = new InlayHintParams(
                new TextDocumentIdentifier("src/test/resources/burp/BURP-FNML+CC-02/mapping.ttl"),
                new Range(new Position(0, 0), new Position(100, 0))
        );
        List<InlayHint> hints = server.getTextDocumentService().inlayHint(hintParams).get();
        List<String> hintLabels = hints.stream().map(h -> h.getLabel().getLeft()).toList();
        assertThat(hintLabels).anyMatch(l -> l.contains("ex:student/1"));
        assertThat(hintLabels).contains(" ⇾ true");
        assertThat(hintLabels).contains(" ⇾ \"admin\"");
        assertThat(hintLabels).contains(" ⇾ ( \"Alice\" \"admin\" )");
    }

    @Test
    public void testJoinConditionSourceInlayHintsInRMLTC0009a() throws ExecutionException, InterruptedException {
        String uri = "rml-modules/rml-core/test-cases/RMLTC0009a-JSON/mapping.ttl";
        RmlLanguageServer server = new RmlDevServerImpl();

        InlayHintParams hintParams = new InlayHintParams(
                new TextDocumentIdentifier(uri),
                new Range(new Position(0, 0), new Position(100, 0))
        );
        List<InlayHint> hints = server.getTextDocumentService().inlayHint(hintParams).get();
        assertThat(hints).isNotNull().isNotEmpty();

        // 1. Verify child source student.json at line 22 (0-indexed 21)
        assertThat(hints)
                .filteredOn(h -> h.getLabel().getLeft().contains("student.json"))
                .as("Inlay hints should contain child source student.json at line 22")
                .extracting(h -> h.getPosition().getLine())
                .containsExactly(21);

        // 2. Verify parent source sport.json at line 23 (0-indexed 22)
        assertThat(hints)
                .filteredOn(h -> h.getLabel().getLeft().contains("sport.json"))
                .as("Inlay hints should contain parent source sport.json at line 23")
                .extracting(h -> h.getPosition().getLine())
                .containsExactly(22);
    }

    @Test
    public void testJoinConditionEvaluationHintsInRMLTC0009a() throws ExecutionException, InterruptedException {
        String uri = "rml-modules/rml-core/test-cases/RMLTC0009a-JSON/mapping.ttl";
        RmlLanguageServer server = new RmlDevServerImpl();

        // 1. Test inlineEvaluate endpoint
        InlineEvaluateParams evalParams = new InlineEvaluateParams(
                uri,
                new AllDataContext()
        );
        List<InlineEvaluateItem> items = server.inlineEvaluate(evalParams).get();
        assertThat(items).isNotNull().isNotEmpty();

        // Find child $.Sport evaluation item (line 22, 0-indexed line 21)
        assertThat(items)
                .filteredOn(i -> i.originRange().getStart().getLine() == 21)
                .flatExtracting(InlineEvaluateItem::values)
                .extracting(v -> v instanceof RawValue rv ? String.valueOf(rv.value()) : v.toString())
                .as("rml:child $.Sport should evaluate to 100 from student.json")
                .contains("100");

        // Find parent $.ID evaluation item (line 23, 0-indexed line 22)
        assertThat(items)
                .filteredOn(i -> i.originRange().getStart().getLine() == 22)
                .flatExtracting(InlineEvaluateItem::values)
                .extracting(v -> v instanceof RawValue rv ? String.valueOf(rv.value()) : v.toString())
                .as("rml:parent $.ID should evaluate to 100 from sport.json (not 10 from student.json)")
                .contains("100")
                .doesNotContain("10");

        // 2. Test standard LSP Inlay Hints endpoint
        InlayHintParams hintParams = new InlayHintParams(
                new TextDocumentIdentifier(uri),
                new Range(new Position(0, 0), new Position(100, 0))
        );
        List<InlayHint> hints = server.getTextDocumentService().inlayHint(hintParams).get();

        assertThat(hints)
                .filteredOn(h -> h.getPosition().getLine() == 21 && h.getLabel().getLeft().startsWith(" ⇾ "))
                .extracting(h -> h.getLabel().getLeft())
                .singleElement()
                .as("Child evaluation hint on line 22 should show 100")
                .asString()
                .contains("100");

        assertThat(hints)
                .filteredOn(h -> h.getPosition().getLine() == 22 && h.getLabel().getLeft().startsWith(" ⇾ "))
                .extracting(h -> h.getLabel().getLeft())
                .singleElement()
                .as("Parent evaluation hint on line 23 should show 100 from sport.json")
                .asString()
                .contains("100")
                .doesNotContain("10\"");
    }

    @Test
    public void testViewJoinInlayHintsAndEvaluation_RMLLVTC0006a() throws ExecutionException, InterruptedException {
        String uri = "rml-modules/rml-lv/test-cases/RMLLVTC0006a/mapping.ttl";
        RmlLanguageServer server = new RmlDevServerImpl();

        // 1. LSP Inlay Hints endpoint
        InlayHintParams hintParams = new InlayHintParams(
                new TextDocumentIdentifier(uri),
                new Range(new Position(0, 0), new Position(100, 0))
        );
        List<InlayHint> hints = server.getTextDocumentService().inlayHint(hintParams).get();
        assertThat(hints).isNotNull().isNotEmpty();

        assertThat(hints)
                .extracting(h -> h.getLabel().getLeft())
                .as("Inlay hints should contain join sources and evaluated values for RMLLVTC0006a")
                .anyMatch(l -> l.contains("people.csv"))
                .anyMatch(l -> l.contains("people.json"))
                .anyMatch(l -> l.contains("person/alice"))
                .anyMatch(l -> l.contains("1995"))
                .anyMatch(l -> l.contains("item/sword"));
    }

    @Test
    public void testViewJoinInlineEvaluate_RMLLVTC0006b_ThirdIteration() throws ExecutionException, InterruptedException {
        String uri = "rml-modules/rml-lv/test-cases/RMLLVTC0006b/mapping.ttl";
        RmlLanguageServer server = new RmlDevServerImpl();

        // 3rd iteration is index 2 (startIndex=2, endIndex=2) -> Bob (1999) with item "flower"
        InlineEvaluateParams params = new InlineEvaluateParams(
                uri,
                new RangeDataContext(2, 2)
        );

        List<InlineEvaluateItem> items = server.inlineEvaluate(params).get();
        assertThat(items).isNotNull().isNotEmpty();

        assertThat(items)
                .flatExtracting(InlineEvaluateItem::values)
                .extracting(v -> switch (v) {
                    case IRITerm iri -> iri.uri();
                    case LiteralTerm lt -> lt.value();
                    case RawValue rv -> String.valueOf(rv.value());
                    default -> v.toString();
                })
                .as("3rd iteration should evaluate to bob (1999) with item flower")
                .contains("http://example.org/person/bob", "1999", "http://example.org/person/bob/item/flower");
    }

    @Test
    public void testReferencingObjectMapInlineEvaluation_RMLTC0009a() throws ExecutionException, InterruptedException {
        String uri = "src/test/resources/burp/rml_tc0009a/mapping.ttl";
        RmlLanguageServer server = new RmlDevServerImpl();

        InlineEvaluateParams params = new InlineEvaluateParams(
                uri,
                new AllDataContext()
        );

        List<InlineEvaluateItem> items = server.inlineEvaluate(params).get();
        assertThat(items).isNotNull().isNotEmpty();

        // Find ReferencingObjectMap item
        var romItem = items.stream()
                .filter(i -> "ReferencingObjectMap".equalsIgnoreCase(i.type()))
                .findFirst()
                .orElse(null);

        assertThat(romItem).isNotNull();
        assertThat(romItem.values())
                .anyMatch(v -> v instanceof IRITerm iri && iri.uri().equals("http://example.com/resource/sport_100"));
    }

    @Test
    public void testMultiSourceDataContext_JoinWithCustomData() throws ExecutionException, InterruptedException {
        String uri = "src/test/resources/burp/rml_tc0009a/mapping.ttl";
        RmlLanguageServer server = new RmlDevServerImpl();

        // Provide custom student and custom sport
        Map<String, Object> studentData = new HashMap<>();
        studentData.put("ID", "99");
        studentData.put("Sport", "999");
        studentData.put("Name", "Custom Player");

        Map<String, Object> sportData = new HashMap<>();
        sportData.put("ID", "999");
        sportData.put("Name", "Custom Sport");

        Map<String, EvaluationDataContext> sources = new HashMap<>();
        sources.put("student.json", new ReferenceValuesCustomDataContext(studentData));
        sources.put("sport.json", new ReferenceValuesCustomDataContext(sportData));

        MultiSourceDataContext multiCtx = new MultiSourceDataContext(sources, null);

        InlineEvaluateParams params = new InlineEvaluateParams(
                uri,
                multiCtx
        );

        List<InlineEvaluateItem> items = server.inlineEvaluate(params).get();
        assertThat(items).isNotNull().isNotEmpty();

        // 1. Child student name should be Custom Player
        assertThat(items)
                .anyMatch(i -> i.values().stream().anyMatch(v -> v instanceof LiteralTerm lt && lt.value().equals("Custom Player")));

        // 2. ReferencingObjectMap should evaluate to http://example.com/resource/sport_999
        var romItem = items.stream()
                .filter(i -> "ReferencingObjectMap".equalsIgnoreCase(i.type()))
                .findFirst()
                .orElse(null);

        assertThat(romItem).isNotNull();
        assertThat(romItem.values())
                .anyMatch(v -> v instanceof IRITerm iri && iri.uri().equals("http://example.com/resource/sport_999"));
    }

    @Test
    public void mapping_with_invalid_reference_other_should_still_work() throws Exception {
        String filePathStr = "src/test/resources/burp/partially_valid_references/mapping.ttl";
        InlineEvaluateParams params = new InlineEvaluateParams(filePathStr, new AllDataContext());

        RmlLanguageServer server = new RmlDevServerImpl();
        List<InlineEvaluateItem> items = server.inlineEvaluate(params).get();
        assertThat(items).flatMap(InlineEvaluateItem::values).anyMatch(v -> v instanceof LiteralTerm lt && lt.value().equals("Gordon"));
        assertThat(items).flatMap(InlineEvaluateItem::values).anyMatch(v -> v instanceof LiteralTerm lt && lt.value().equals("85"));

        InlayHintParams hintParams = new InlayHintParams(
                new TextDocumentIdentifier(filePathStr),
                new Range(new Position(0, 0), new Position(26, 0))
        );
        List<InlayHint> hints = server.getTextDocumentService().inlayHint(hintParams).get();

        List<String> hintLabels = hints.stream().map(h -> h.getLabel().getLeft()).toList();
        assertThat(hintLabels).anyMatch(l -> l.contains("Gordon"));
        assertThat(hintLabels).anyMatch(l -> l.contains("85"));

        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(filePathStr)).get();
        assertThat(diagnostics).anyMatch(d -> d.message().contains("email"));
    }

    @Test
    public void mapping_with_invalid_template_should_evaluate_other_nodes_in_lenient_mode() throws Exception {
        String filePathStr = "src/test/resources/burp/inline_eval_invalid_template/mapping.ttl";
        RmlLanguageServer server = new RmlDevServerImpl();

        // Inline evaluate in lenient mode:
        // SubjectMap (invalid template producing invalid IRI) should not produce terms,
        // but ObjectMap should still produce "Alice"
        InlineEvaluateParams params = new InlineEvaluateParams(filePathStr, new AllDataContext());
        List<InlineEvaluateItem> items = server.inlineEvaluate(params).get();
        assertThat(items).isNotNull();

        assertThat(items).flatMap(InlineEvaluateItem::values)
                .anyMatch(v -> v instanceof LiteralTerm lt && lt.value().equals("Alice"));

        items.stream()
                .filter(i -> "SubjectMap".equalsIgnoreCase(i.type()))
                .findFirst().ifPresent(subjectMapItem -> assertThat(subjectMapItem.values()).isEmpty());
    }

    @Test
    public void mapping_with_invalid_template_should_report_diagnostic_without_constant_suggestion() throws Exception {
        String filePathStr = "src/test/resources/burp/inline_eval_invalid_template/mapping.ttl";
        RmlLanguageServer server = new RmlDevServerImpl();

        // LSP validate reports diagnostic for missing reference segment in template.
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(filePathStr)).get();
        assertThat(diagnostics).anyMatch(d ->
                d.severity() == DiagnosticSeverity.Error
                        && RER.TemplateSyntaxError.getURI().equals(d.rerCodeUri())
                        && d.message().contains("rml:constant")
        );
        assertThat(diagnostics).noneMatch(d -> d.message().contains("Use rml:constant <"));
    }

    @Test
    public void mapping_with_constant_like_template_should_suggest_constant_during_evaluation_and_produce_valid_iri() throws Exception {
        String filePathStr = "src/test/resources/burp/inline_eval_constant_template/mapping.ttl";
        RmlLanguageServer server = new RmlDevServerImpl();

        // 1. LSP validate reports syntax error that template lacks reference segment / should be constant
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(filePathStr)).get();
        assertThat(diagnostics).anyMatch(d ->
                d.severity() == DiagnosticSeverity.Error &&
                        RER.TemplateSyntaxError.getURI().equals(d.rerCodeUri())
        );

        // 2. Evaluation in lenient mode produces the valid IRI and suggests rml:constant
        InlineEvaluateParams params = new InlineEvaluateParams(filePathStr, new AllDataContext());
        List<InlineEvaluateItem> items = server.inlineEvaluate(params).get();
        assertThat(items).isNotNull();

        assertThat(items).flatMap(InlineEvaluateItem::values)
                .anyMatch(v -> v instanceof IRITerm it && it.uri().equals("http://example.org/id"));

        assertThat(items).flatMap(InlineEvaluateItem::values)
                .anyMatch(v -> v instanceof LiteralTerm lt && lt.value().equals("Alice"));

        assertThat(items).flatMap(InlineEvaluateItem::diagnostics)
                .anyMatch(msg -> msg.contains("rml:constant <http://example.org/id>"));
    }

    @Test
    public void mapping_with_base_iri_on_triples_map_should_suggest_resolved_constant_and_produce_resolved_iri() throws Exception {
        String filePathStr = "src/test/resources/burp/inline_eval_constant_relative_template/mapping.ttl";
        RmlLanguageServer server = new RmlDevServerImpl();

        // LSP validate reports syntax error that template lacks reference segment / should be constant
        List<RmlDiagnostic> diagnostics = server.validate(new ValidateParams(filePathStr)).get();
        assertThat(diagnostics).anyMatch(d ->
                d.severity() == DiagnosticSeverity.Error &&
                        RER.TemplateSyntaxError.getURI().equals(d.rerCodeUri())
        );

        // Inline evaluation in lenient mode uses TriplesMap BaseIRIScope to resolve relative template against baseIRI
        InlineEvaluateParams params = new InlineEvaluateParams(filePathStr, new AllDataContext());
        List<InlineEvaluateItem> items = server.inlineEvaluate(params).get();
        assertThat(items).isNotNull();

        assertThat(items).flatMap(InlineEvaluateItem::values)
                .anyMatch(v -> v instanceof IRITerm it && it.uri().equals("http://example.com/base/relative/person"));

        assertThat(items).flatMap(InlineEvaluateItem::values)
                .anyMatch(v -> v instanceof LiteralTerm lt && lt.value().equals("Alice"));
    }

    @Test
    public void mapping_with_base_iri_on_triples_map_should_produce_resolved_iri() throws Exception {
        String filePathStr = "src/test/resources/burp/inline_eval_relative_template/mapping.ttl";
        RmlLanguageServer server = new RmlDevServerImpl();

        // Inline evaluation in lenient mode uses TriplesMap BaseIRIScope to resolve relative template against baseIRI
        InlineEvaluateParams params = new InlineEvaluateParams(filePathStr, new AllDataContext());
        List<InlineEvaluateItem> items = server.inlineEvaluate(params).get();
        assertThat(items).isNotNull();

        assertThat(items).flatMap(InlineEvaluateItem::values)
                .filteredOn(v -> v instanceof IRITerm)
                .anyMatch(it -> ((IRITerm) it).uri().equals("http://example.com/base/relative/person/Alice"));

        assertThat(items).flatMap(InlineEvaluateItem::values)
                .filteredOn(v -> v instanceof LiteralTerm)
                .anyMatch(v -> ((LiteralTerm) v).value().equals("Alice"));
    }

    @Test
    public void mapping_should_produce_resolved_iri_from_config() throws Exception {
        String filePathStr = "src/test/resources/burp/inline_eval_relative_template_from_config/mapping.ttl";
        RmlLanguageServer server = new RmlDevServerImpl();

        // Send config
        MappingWorkspace.Settings settings = new MappingWorkspace.Settings(new MappingWorkspace.RmlSettings("http://example.com/base/", null));
        var gson = new Gson();
        JsonElement settingsJson = gson.toJsonTree(settings);
        server.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams(settingsJson));

        // Inline evaluation in lenient mode uses TriplesMap BaseIRIScope to resolve relative template against baseIRI
        InlineEvaluateParams params = new InlineEvaluateParams(filePathStr, new AllDataContext());
        List<InlineEvaluateItem> items = server.inlineEvaluate(params).get();
        assertThat(items).isNotNull();

        assertThat(items).flatMap(InlineEvaluateItem::values)
                .filteredOn(v -> v instanceof IRITerm)
                .anyMatch(it -> ((IRITerm) it).uri().equals("http://example.com/base/relative/person/Alice"));

        assertThat(items).flatMap(InlineEvaluateItem::values)
                .filteredOn(v -> v instanceof LiteralTerm)
                .anyMatch(v -> ((LiteralTerm) v).value().equals("Alice"));
    }

    @Test
    public void test1000recordsTemplateAndSegmentInlayHints() throws Exception {
        String filePathStr = "/home/jakub/Documents/Dev/RmlTests/1000records.rml.ttl";
        RmlDevServerImpl server = new RmlDevServerImpl();
        server.getWorkspace().setBaseIri("http://example.com/base/");

        InlineEvaluateParams params = new InlineEvaluateParams(filePathStr, new RangeDataContext(0, 1));
        List<InlineEvaluateItem> items = server.inlineEvaluate(params).get();
        assertThat(items).isNotNull().isNotEmpty();

        // 1. Reference segment inside template evaluates to literal "100" (first row)
        assertThat(items).anyMatch(i ->
                "ReferenceSegment".equals(i.type()) &&
                        i.values().stream().anyMatch(v -> v instanceof LiteralTerm lt && lt.value().equals("100"))
        );

        // 2. SubjectMap evaluates to full IRI resolved against base IRI
        assertThat(items).anyMatch(i ->
                "SubjectMap".equals(i.type()) &&
                        i.values().stream().anyMatch(v -> v instanceof IRITerm iri && iri.uri().equals("http://example.com/base/person_100"))
        );

        // 3. Template evaluates to interpolated string value
        assertThat(items).anyMatch(i ->
                "Template".equals(i.type()) &&
                        i.values().stream().anyMatch(v -> v instanceof RawValue rv && rv.value().equals("person_100"))
        );

        // 4. LSP Inlay Hints endpoint produces all three distinct hints
        InlayHintParams hintParams = new InlayHintParams(
                new TextDocumentIdentifier(filePathStr),
                new Range(new Position(0, 0), new Position(100, 0))
        );
        List<InlayHint> hints = server.getTextDocumentService().inlayHint(hintParams).get();
        assertThat(hints).isNotNull().isNotEmpty();
        List<String> hintLabels = hints.stream().map(h -> h.getLabel().getLeft()).toList();
        assertThat(hintLabels).anyMatch(l -> l.contains("100") || l.contains("\"100\""));
        assertThat(hintLabels).anyMatch(l -> l.contains("http://example.com/base/person_100"));
    }

    @Test
    public void testThreeDistinctInlayHintsForTermMapTemplateAndReference() throws Exception {
        String uri = "src/test/resources/burp/eval_alice/mapping.ttl";
        RmlLanguageServer server = new RmlDevServerImpl();

        InlayHintParams hintParams = new InlayHintParams(
                new TextDocumentIdentifier(uri),
                new Range(new Position(0, 0), new Position(100, 0))
        );
        List<InlayHint> hints = server.getTextDocumentService().inlayHint(hintParams).get();
        assertThat(hints).isNotNull().isNotEmpty();

        // 1. Line 13 (0-indexed 12): TermMap / SubjectMap hint after '['
        assertThat(hints)
                .filteredOn(h -> h.getPosition().getLine() == 12)
                .extracting(h -> h.getLabel().getLeft())
                .anyMatch(l -> l.contains("ex:person/Alice"));

        // 2. Line 14 (0-indexed 13): Template literal string and ReferenceSegment hints
        assertThat(hints)
                .filteredOn(h -> h.getPosition().getLine() == 13)
                .extracting(h -> h.getLabel().getLeft())
                .anyMatch(l -> l.contains("http://example.org/person/Alice"))
                .anyMatch(l -> l.contains("Alice"));
    }
}
