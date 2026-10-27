package burp.dev;

import burp.dev.rml.RmlDevServerImpl;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionCodeLensTest {

    @Test
    public void testFunctionCodeLensGeneration() throws ExecutionException, InterruptedException {
        RmlDevServerImpl server = new RmlDevServerImpl();
        String mappingUri = "src/test/resources/burp/BURPFNMLTC-CSV-Concat/mapping.ttl";

        List<? extends CodeLens> lenses = server.getTextDocumentService()
                .codeLens(new CodeLensParams(new TextDocumentIdentifier(mappingUri)))
                .get();

        assertThat(lenses).isNotNull().isNotEmpty();

        // Expect CodeLens at BOTH the definition site (line 19) and usage site (line 14)
        // Function name is resolved from ontology name: "concat(" instead of "idlab-fn:concat("
        assertThat(lenses)
                .filteredOn(l -> l.getCommand() != null && l.getCommand().getTitle().startsWith("concat("))
                .hasSize(2)
                .allSatisfy(l -> {
                    String title = l.getCommand().getTitle();
                    assertThat(title)
                            .isEqualTo("concat(input string = Name, other input string = Class, delimiter = \" - \")");
                })
                .extracting(l -> l.getRange().getStart().getLine())
                .containsExactlyInAnyOrder(14, 19);
    }
}
