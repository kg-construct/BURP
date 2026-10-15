package burp.dev;

import burp.dev.rml.RmlDevServerImpl;
import burp.dev.rml.RmlLanguageServer;
import burp.dev.rml.service.selector.CursorSelector;
import burp.dev.rml.service.selector.IriSelector;
import burp.dev.rml.service.source.SourceDataParams;
import burp.dev.rml.service.source.SourceDataResult;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

public class SourceDataTest {

    private static final String MAPPING_URI = "src/test/resources/burp/eval_logicalview/mapping.ttl";

    @Test
    public void testCodeLensGenerationForLogicalViews() throws ExecutionException, InterruptedException {
        RmlDevServerImpl server = new RmlDevServerImpl();
        List<? extends CodeLens> lenses = server.getTextDocumentService()
                .codeLens(new CodeLensParams(new TextDocumentIdentifier(MAPPING_URI)))
                .get();

        assertThat(lenses).isNotNull().hasSize(4);
        assertThat(lenses)
                .extracting(l -> l.getCommand().getTitle())
                .containsExactlyInAnyOrder(
                        "$(table) View Logical Source Data",
                        "$(table) View Logical View Data",
                        "$(table) View Logical Source Data",
                        "$(table) View Logical View Data"
                );
    }

    @Test
    public void testCodeLensGenerationForLogicalSources() throws ExecutionException, InterruptedException {
        RmlDevServerImpl server = new RmlDevServerImpl();
        String mappingUri = "src/test/resources/burp/eval_alice/mapping.ttl";
        List<? extends CodeLens> lenses = server.getTextDocumentService()
                .codeLens(new CodeLensParams(new TextDocumentIdentifier(mappingUri)))
                .get();

        assertThat(lenses).isNotNull().isNotEmpty();
        assertThat(lenses.stream().map(l -> l.getCommand().getTitle()))
                .anyMatch(title -> title.equals("$(table) View Logical Source Data"));
    }

    @Test
    public void testPreviewJsonLogicalViewWithPagination() throws ExecutionException, InterruptedException {
        RmlLanguageServer server = new RmlDevServerImpl();

        // Page 1 (offset = 0, limit = 1)
        SourceDataParams params1 = new SourceDataParams(
                new IriSelector(MAPPING_URI, "http://example.org/jsonView"),
                0,
                1,
                null
        );

        SourceDataResult result1 = server.getSourceData(params1).get();
        assertThat(result1.diagnostics()).isEmpty();
        assertThat(result1.structureId()).isNotBlank();
        assertThat(result1.structureResource()).isEqualTo("http://example.org/jsonView");
        assertThat(result1.format()).isEqualTo("table");
        assertThat(result1.table()).isNotNull();
        assertThat(result1.table().headers()).contains("name", "item.type");
        assertThat(result1.totalItems()).isEqualTo(2);
        assertThat(result1.totalExact()).isTrue();
        assertThat(result1.hasMore()).isTrue();
        assertThat(result1.table().rows()).hasSize(1);
        assertThat(result1.table().rows().getFirst()).contains("Alice", "Book");

        // Page 2 (offset = 1, limit = 1)
        SourceDataParams params2 = new SourceDataParams(
                new IriSelector(MAPPING_URI, "http://example.org/jsonView"),
                1,
                1,
                null
        );

        SourceDataResult result2 = server.getSourceData(params2).get();
        assertThat(result2.table()).isNotNull();
        assertThat(result2.table().rows()).hasSize(1);
        assertThat(result2.hasMore()).isFalse();
        assertThat(result2.table().rows().getFirst()).contains("Alice", "Laptop");
    }

    @Test
    public void testPreviewJsonLogicalSourceDocumentFormat() throws ExecutionException, InterruptedException {
        RmlLanguageServer server = new RmlDevServerImpl();

        SourceDataParams params = new SourceDataParams(
                new IriSelector(MAPPING_URI, "http://example.org/jsonSource"),
                0,
                10,
                null
        );

        SourceDataResult result = server.getSourceData(params).get();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.format()).isEqualTo("json");
        assertThat(result.document()).isNotNull();
        assertThat(result.document().language()).isEqualTo("json");
        assertThat(result.document().items()).isNotEmpty();
        assertThat(result.document().items().getFirst()).contains("Alice");
    }

    @Test
    public void testPreviewCsvLogicalViewWithJoin() throws ExecutionException, InterruptedException {
        RmlLanguageServer server = new RmlDevServerImpl();

        SourceDataParams params = new SourceDataParams(
                new CursorSelector(MAPPING_URI, new Position(37, 10)),
                0,
                10,
                null
        );

        SourceDataResult result = server.getSourceData(params).get();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.format()).isEqualTo("table");
        assertThat(result.table()).isNotNull();
        assertThat(result.table().headers()).contains("name", "id", "birthyear", "item_type");
        assertThat(result.totalItems()).isEqualTo(2);
        assertThat(result.totalExact()).isTrue();
        assertThat(result.hasMore()).isFalse();
        assertThat(result.table().rows()).hasSize(2);

        List<String> firstRow = result.table().rows().get(0);
        assertThat(firstRow).contains("Alice", "1", "1995", "Book");

        List<String> secondRow = result.table().rows().get(1);
        assertThat(secondRow).contains("Alice", "1", "1995", "Laptop");
    }
}
