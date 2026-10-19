package burp.dev;

import burp.dev.rml.RmlDevServerImpl;
import burp.dev.rml.RmlLanguageServer;
import burp.dev.rml.service.selector.CursorSelector;
import org.eclipse.lsp4j.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CompleteReferenceTest {

    private List<CompletionItem> complete(RmlLanguageServer server, String uri, Position position) throws Exception {
        var either = server.getTextDocumentService().completion(new CompletionParams(new TextDocumentIdentifier(uri), position)).get();
        if (either == null) return List.of();
        if (either.isLeft()) return either.getLeft();
        return either.getRight().getItems();
    }

    @Test
    public void testCompleteReferenceWithPrefixOverridingFileReference() throws Exception {
        String uri = "src/test/resources/burp/eval_alice/mapping.ttl";
        RmlDevServerImpl server = new RmlDevServerImpl();
        // Position on line 19 (0-indexed 18), column 28: after "$." -> matches both $.name and $.age
        List<CompletionItem> completions = complete(server, uri, new Position(18, 28));

        assertThat(completions).isNotNull().isNotEmpty();

        var ageComp = completions.stream().filter(c -> "$.age".equals(c.getLabel())).findFirst().orElse(null);
        assertThat(ageComp).as("$.age completion should be present").isNotNull();
        assertThat(ageComp.getKind()).isEqualTo(CompletionItemKind.Field);
        assertThat(ageComp.getDetail()).contains("JSONPath");

        var nameComp = completions.stream().filter(c -> "$.name".equals(c.getLabel())).findFirst().orElse(null);
        assertThat(nameComp).as("$.name completion should be present").isNotNull();
        assertThat(nameComp.getDetail()).contains("Alice");
    }

    @Test
    public void testCompleteReferenceWithoutPrefixFromNewResource() throws Exception {
        String uri = "src/test/resources/burp/complete_reference_schema/mapping.ttl";
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<CompletionItem> completions = complete(server, uri, new Position(17, 28));

        assertThat(completions).isNotNull().isNotEmpty();

        var firstComp = completions.stream().filter(c -> "$.firstName".equals(c.getLabel())).findFirst().orElse(null);
        assertThat(firstComp).as("$.firstName completion should be present").isNotNull();
        assertThat(firstComp.getKind()).isEqualTo(CompletionItemKind.Field);
        assertThat(firstComp.getDetail()).contains("Alice");
    }

    @Test
    public void testCompleteReferenceWithEmptyPrefixFromSchema() throws Exception {
        String uri = "src/test/resources/burp/complete_reference_schema/mapping.ttl";
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<CompletionItem> completions = complete(server, uri, new Position(23, 27));

        assertThat(completions)
                .isNotNull()
                .hasSizeGreaterThanOrEqualTo(3);

        assertThat(completions)
                .extracting(CompletionItem::getLabel)
                .contains("$.firstName", "$.lastName", "$.contact.email");
    }

    @Test
    public void testCompleteReferenceFromCsv() throws Exception {
        String uri = "src/test/resources/burp/BURPFNMLTC-CSV-Concat/mapping.ttl";
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<CompletionItem> completions = complete(server, uri, new Position(10, 1));

        assertThat(completions).isNotNull().isNotEmpty();

        var nameComp = completions.stream().filter(c -> "Name".equals(c.getLabel())).findFirst().orElse(null);
        assertThat(nameComp).as("Name column from CSV header should be present").isNotNull();
        assertThat(nameComp.getKind()).isEqualTo(CompletionItemKind.Field);
        assertThat(nameComp.getDetail()).contains("CSV").contains("Gaston");
    }

    @Test
    public void testCompleteReferenceFromLogicalView() throws Exception {
        String uri = "rml-modules/rml-lv/test-cases/RMLLVTC0000c/mapping.ttl";
        RmlDevServerImpl server = new RmlDevServerImpl();
        // Line 32 (0-indexed 31), column 45: inside rml:template "{newName}"
        List<CompletionItem> completions = complete(server, uri, new Position(31, 45));

        assertThat(completions).isNotNull().isNotEmpty();

        var newNameComp = completions.stream().filter(c -> "newName".equals(c.getLabel())).findFirst().orElse(null);
        assertThat(newNameComp).as("newName field from logical view should be present").isNotNull();
        assertThat(newNameComp.getKind()).isEqualTo(CompletionItemKind.Field);
        assertThat(newNameComp.getDetail()).contains("LogicalView");
    }

    @Test
    public void testCompleteReferenceInsideLogicalViewFieldDefinition() throws Exception {
        String uri = "rml-modules/rml-lv/test-cases/RMLLVTC0000c/mapping.ttl";
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<CompletionItem> completions = complete(server, uri, new Position(25, 20));

        assertThat(completions).isNotNull().isNotEmpty();

        var nameComp = completions.stream().filter(c -> "name".equals(c.getLabel())).findFirst().orElse(null);
        assertThat(nameComp)
                .as("Field definition in :jsonViewOnJsonView should complete 'name' from viewed :jsonView")
                .isNotNull();
        assertThat(nameComp.getKind()).isEqualTo(CompletionItemKind.Field);
    }

    @Test
    public void testCompleteReferenceInJoinConditionChild() throws Exception {
        String uri = "rml-modules/rml-core/test-cases/RMLTC0030a-JSON/mapping.ttl";
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<CompletionItem> childCompletions = complete(server, uri, new Position(22, 45));
        assertThat(childCompletions).isNotNull();
        var sportComp = childCompletions.stream().filter(c -> "$.Sport".equals(c.getLabel())).findFirst().orElse(null);
        assertThat(sportComp)
                .as("Child join reference should complete $.Sport from child logical source (student.json)")
                .isNotNull();
    }

    @Test
    public void testCompleteReferenceInJoinConditionParent() throws Exception {
        String uri = "rml-modules/rml-core/test-cases/RMLTC0030a-JSON/mapping.ttl";
        RmlDevServerImpl server = new RmlDevServerImpl();
        // Position on line 22 (0-indexed 21), column 46: after "$." -> matches $.ID and $.Name
        List<CompletionItem> parentCompletions = complete(server, uri, new Position(21, 46));
        assertThat(parentCompletions).isNotNull();
        var nameComp = parentCompletions.stream().filter(c -> "$.Name".equals(c.getLabel())).findFirst().orElse(null);
        assertThat(nameComp)
                .as("Parent join reference should complete $.Name from parent logical source (sport.json)")
                .isNotNull();
    }

    @Test
    public void testCompleteReferenceInRMLTC0009a_Child() throws Exception {
        String uri = "rml-modules/rml-core/test-cases/RMLTC0009a-JSON/mapping.ttl";
        RmlDevServerImpl server = new RmlDevServerImpl();
        // 1-indexed (22, 28) -> 0-indexed (21, 27) inside "$.Sport"
        List<CompletionItem> childCompletions = complete(server, uri, new Position(21, 27));
        assertThat(childCompletions).isNotNull().isNotEmpty();

        List<String> labels = childCompletions.stream().map(CompletionItem::getLabel).toList();
        assertThat(labels)
                .as("Child join completion for TriplesMap1 should contain $.ID, $.Sport, $.Name from student.json")
                .containsExactlyInAnyOrder("$.ID", "$.Name", "$.Sport");
    }

    @Test
    public void testCompleteReferenceInRMLTC0009a_Parent() throws Exception {
        String uri = "rml-modules/rml-core/test-cases/RMLTC0009a-JSON/mapping.ttl";
        RmlDevServerImpl server = new RmlDevServerImpl();
        // 1-indexed (23, 29) -> 0-indexed (22, 28) inside "$.ID"
        List<CompletionItem> parentCompletions = complete(server, uri, new Position(22, 28));
        assertThat(parentCompletions).isNotNull().isNotEmpty();

        List<String> labels = parentCompletions.stream().map(CompletionItem::getLabel).toList();
        assertThat(labels)
                .as("Parent join completion for TriplesMap2 should contain $.ID, $.Name from sport.json")
                .containsExactlyInAnyOrder("$.ID", "$.Name");
        assertThat(labels)
                .as("Parent join completion should not contain $.Sport")
                .doesNotContain("$.Sport");
    }

    @Test
    public void testCompleteReferenceIncludesReplacementRangeAndGraphPointer() throws Exception {
        String uri = "src/test/resources/burp/eval_alice/mapping.ttl";
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<CompletionItem> completions = complete(server, uri, new Position(18, 30));

        assertThat(completions).isNotNull().isNotEmpty();

        var ageComp = completions.stream().filter(c -> "$.age".equals(c.getLabel())).findFirst().orElse(null);
        assertThat(ageComp).isNotNull();
        assertThat(ageComp.getTextEdit())
                .as("Completion should contain the document TextEdit")
                .isNotNull();
        assertThat(ageComp.getTextEdit().getLeft().getRange().getStart().getLine()).isEqualTo(18);
    }

    @Test
    public void testCompleteParameterInFunctionExecution() throws Exception {
        String mappingTurtle = """
                @prefix rml: <http://w3id.org/rml/> .
                @prefix grel: <http://users.ugent.be/~bjdmeest/function/grel.ttl#> .
                @prefix ex: <http://example.com/> .

                <#TM> a rml:TriplesMap ;
                    rml:logicalSource [
                        rml:source [ a rml:RelativePathSource ; rml:root rml:MappingDirectory ; rml:path "student.csv" ] ;
                        rml:referenceFormulation rml:CSV
                    ] ;
                    rml:subjectMap [
                        rml:template "http://example.com/{id}"
                    ] ;
                    rml:predicateObjectMap [
                        rml:predicate ex:upper ;
                        rml:objectMap [
                            rml:functionExecution [
                                rml:function grel:toUpperCase ;
                                rml:input [
                                    rml:parameter grel:val ;
                                    rml:inputValue "test"
                                ]
                            ]
                        ]
                    ] .
                """;

        String uri = "file:///test-func-param-completion.ttl";
        RmlDevServerImpl server = new RmlDevServerImpl();
        server.getWorkspace().updateDocumentText(uri, mappingTurtle);

        // Line 19 (0-indexed: 18), column 36: on "grel:val"
        List<CompletionItem> completions = complete(server, uri, new Position(18, 36));
        assertThat(completions).isNotEmpty();

        List<String> refs = completions.stream().map(CompletionItem::getLabel).toList();
        assertThat(refs).contains("grel:valueParam", "grel:valueParameter");
        assertThat(refs).doesNotContain("id", "name", "role");

        var paramComp = completions.stream().filter(c -> "grel:valueParam".equals(c.getLabel())).findFirst().orElseThrow();
        assertThat(paramComp.getKind()).isEqualTo(CompletionItemKind.Property);
        assertThat(paramComp.getDetail()).contains("input value").contains("[required]");
    }

    @Test
    public void testResolveNodeAtParameterPosition() throws Exception {
        Path p = Path.of("src/test/resources/burp/complete_function/mapping.ttl").toAbsolutePath();
        String uri = p.toUri().toString();
        RmlDevServerImpl server = new RmlDevServerImpl();
        server.getWorkspace().updateDocumentText(uri, Files.readString(p));

        var resolved = server.getWorkspace().resolveNode(new CursorSelector(uri, new Position(21, 31)));
        burp.model.ParameterMap pm;
        if (resolved.targetNode() instanceof burp.model.ParameterMap paramMap) {
            pm = paramMap;
        } else if (resolved.targetNode() instanceof burp.model.RDFNodeConstant c && c.getParent() instanceof burp.model.ParameterMap paramMap) {
            pm = paramMap;
        } else {
            throw new AssertionError("Expected ParameterMap or RDFNodeConstant child, but was: " + resolved.targetNode());
        }
        assertThat(pm.getParent()).isInstanceOf(burp.model.Input.class);
        burp.model.Input inputNode = (burp.model.Input) pm.getParent();
        assertThat(inputNode.getParent()).isInstanceOf(burp.model.fnml.FunctionExecution.class);
    }

    @Test
    public void testCompleteParameterInCompleteFunction() throws Exception {
        Path p = Path.of("src/test/resources/burp/complete_function/mapping.ttl").toAbsolutePath();
        String uri = p.toUri().toString();
        RmlDevServerImpl server = new RmlDevServerImpl();
        server.getWorkspace().updateDocumentText(uri, Files.readString(p));

        List<CompletionItem> completions = complete(server, uri, new Position(21, 31));
        System.out.println("Completions returned: " + completions.stream().map(CompletionItem::getLabel).toList());
        assertThat(completions).isNotEmpty();
        List<String> refs = completions.stream().map(CompletionItem::getLabel).toList();
        assertThat(refs).containsAnyOf("idlab-fn:str", "idlab-fn:_str");
        assertThat(refs).doesNotContain("idlab-fn:otherStr", "idlab-fn:_otherStr", "idlab-fn:delimiter", "idlab-fn:_delimiter");
    }

    @Test
    public void testCompleteParameterInBURPFNMLTC_CSV_Concat() throws Exception {
        Path p = Path.of("src/test/resources/burp/BURPFNMLTC-CSV-Concat/mapping.ttl").toAbsolutePath();
        String uri = p.toUri().toString();
        RmlDevServerImpl server = new RmlDevServerImpl();
        server.getWorkspace().updateDocumentText(uri, Files.readString(p));

        List<CompletionItem> completions = complete(server, uri, new Position(22, 32));
        assertThat(completions).isNotEmpty();
    }

    @Test
    public void testCompleteFunctionInFunctionMap() throws Exception {
        String mappingTurtle = """
                @prefix rml: <http://w3id.org/rml/> .
                @prefix grel: <http://users.ugent.be/~bjdmeest/function/grel.ttl#> .
                @prefix ex: <http://example.com/> .

                <#TM> a rml:TriplesMap ;
                    rml:logicalSource [
                        rml:source [ a rml:RelativePathSource ; rml:root rml:MappingDirectory ; rml:path "student.csv" ] ;
                        rml:referenceFormulation rml:CSV
                    ] ;
                    rml:subjectMap [
                        rml:template "http://example.com/{id}"
                    ] ;
                    rml:predicateObjectMap [
                        rml:predicate ex:upper ;
                        rml:objectMap [
                            rml:functionExecution [
                                rml:function grel:to ;
                                rml:input [
                                    rml:parameter grel:valueParam ;
                                    rml:inputValue "test"
                                ]
                            ]
                        ]
                    ] .
                """;

        String uri = "file:///test-func-name-completion.ttl";
        RmlDevServerImpl server = new RmlDevServerImpl();
        server.getWorkspace().updateDocumentText(uri, mappingTurtle);

        // Line 17 (0-indexed: 16), column 32: on "grel:to"
        List<CompletionItem> completions = complete(server, uri, new Position(16, 32));
        assertThat(completions).isNotEmpty();

        List<String> refs = completions.stream().map(CompletionItem::getLabel).toList();
        assertThat(refs).contains("grel:toUpperCase", "grel:toLowerCase");
        assertThat(refs).doesNotContain("id", "name");

        var funcComp = completions.stream().filter(c -> "grel:toUpperCase".equals(c.getLabel())).findFirst().orElseThrow();
        assertThat(funcComp.getKind()).isEqualTo(CompletionItemKind.Function);
    }

    @Test
    public void testCompleteReferenceFromSqlTable() throws Exception {
        String uri = Path.of("src/test/resources/burp/BURPIOTC-SQLITE/mapping.ttl").toUri().toString();
        RmlDevServerImpl server = new RmlDevServerImpl();
        // Line 19 (0-indexed 18), column 53: right after '{' before 'id}'
        List<CompletionItem> completions = complete(server, uri, new Position(18, 53));

        assertThat(completions).isNotNull().isNotEmpty();

        List<String> labels = completions.stream().map(CompletionItem::getLabel).toList();
        assertThat(labels).contains("id", "name", "firstname");

        var idComp = completions.stream().filter(c -> "id".equals(c.getLabel())).findFirst().orElse(null);
        assertThat(idComp).isNotNull();
        assertThat(idComp.getKind()).isEqualTo(CompletionItemKind.Field);
        assertThat(idComp.getDetail()).contains("SQL2008Table");
    }

    @Test
    public void testCompleteReferenceFromSqlQuery() throws Exception {
        String uri = Path.of("src/test/resources/burp/BURPIOTC-SQLITE/query_mapping.ttl").toUri().toString();
        RmlDevServerImpl server = new RmlDevServerImpl();

        // Line 16 (0-indexed 15), column 42: right after '{' before 'id}'
        List<CompletionItem> completions = complete(server, uri, new Position(15, 42));
        assertThat(completions).isNotEmpty();

        List<String> labels = completions.stream().map(CompletionItem::getLabel).toList();
        assertThat(labels).contains("id", "name");
        assertThat(labels).doesNotContain("firstname");
    }
}


