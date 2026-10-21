package burp;

import burp.dev.rml.service.MappingWorkspace;
import burp.model.TriplesMap;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.TextFilePointer;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MappingWorkspaceTest {

    @Test
    public void testParseMappingInMemoryWithoutDiskFiles() throws Exception {
        MappingWorkspace workspace = new MappingWorkspace();
        workspace.setCurrentWorkingDirectory(Path.of("").toAbsolutePath());

        String uri = "file://" + Path.of("src/test/resources/burp/eval_alice/mapping.ttl").toAbsolutePath();
        String mappingContent = """
                @prefix rml: <http://w3id.org/rml/> .
                @prefix ex: <http://example.org/> .

                ex:TriplesMap a rml:TriplesMap ;
                    rml:logicalSource [
                        rml:source [
                            a rml:RelativePathSource ;
                            rml:root rml:MappingDirectory ;
                            rml:path "student.json"
                        ] ;
                        rml:referenceFormulation rml:JSONPath ;
                        rml:iterator "$.students[*]"
                    ] ;
                    rml:subjectMap [
                        rml:template "http://example.org/student/{$.id}"
                    ] .
                """;

        // Open in-memory buffer
        workspace.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(
                new TextDocumentItem(uri, "turtle", 1, mappingContent)
        ));

        List<TriplesMap> maps = workspace.loadMapping(uri);
        assertThat(maps).hasSize(1);
        assertThat(workspace.getPrefixMap(uri)).containsEntry("rml", "http://w3id.org/rml/");
    }

    @Test
    public void testSyntaxErrorReportsTextFilePointer() {
        MappingWorkspace workspace = new MappingWorkspace();
        String uri = "file:///workspace/test_mapping.ttl";
        String invalidContent = """
                @prefix rml: <http://w3id.org/rml/> .
                ex:InvalidMapping a rml:TriplesMap
                """; // missing closing dot or syntax error

        workspace.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(
                new TextDocumentItem(uri, "turtle", 1, invalidContent)
        ));

        BurpException ex = assertThrows(BurpException.class, () -> workspace.loadMapping(uri));
        assertThat(ex.getError()).isNotNull();
        Origin origin = ex.getError().origin();
        assertThat(origin).isNotNull();
        assertThat(origin.sourceStatements()).isNotEmpty();
        assertThat(origin.sourceStatements().getFirst()).isInstanceOf(TextFilePointer.class);
        TextFilePointer tfp = (TextFilePointer) origin.sourceStatements().getFirst();
        assertThat(tfp.path()).isEqualTo(Path.of("/workspace/test_mapping.ttl"));
    }

    @Test
    public void testUpdateSettingsIterationSampleLimit() {
        MappingWorkspace workspace = new MappingWorkspace();
        assertThat(workspace.getIterationSampleLimit()).isNull();

        workspace.updateSettings(Map.of("rml", Map.of("iterationSampleLimit", 50)));
        assertThat(workspace.getIterationSampleLimit()).isEqualTo(50);

        Map<String, Object> rmlNullLimit = new java.util.HashMap<>();
        rmlNullLimit.put("iterationSampleLimit", null);
        workspace.updateSettings(Map.of("rml", rmlNullLimit));
        assertThat(workspace.getIterationSampleLimit()).isNull();

        workspace.updateSettings(Map.of("iterationSampleLimit", 10));
        assertThat(workspace.getIterationSampleLimit()).isEqualTo(10);
    }
}
