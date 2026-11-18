package burp.processor;

import burp.model.LogicalTarget;
import burp.model.MappingDocument;
import burp.model.RdfStatement;
import burp.parse.RmlLoader;
import org.apache.jena.query.Dataset;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class RmlProcessorTest {

    @Test
    public void testExecuteToDatasetsAndStatements() throws Exception {
        Path mappingPath = Path.of("src/test/resources/burp/eval_alice/mapping.ttl");
        RmlLoader loader = new RmlLoader(mappingPath);
        MappingDocument doc = loader.loadMappingDocument();

        RmlProcessor processor = new RmlProcessor();

        // 1. Statements per target
        Map<LogicalTarget, List<RdfStatement>> stmtsByTarget = processor.executeToStatements(doc);
        assertThat(stmtsByTarget).isNotEmpty();
        assertThat(stmtsByTarget).containsKey(LogicalTarget.DEFAULT);

        List<RdfStatement> defaultStmts = stmtsByTarget.get(LogicalTarget.DEFAULT);
        assertThat(defaultStmts).isNotEmpty();

        // 2. Datasets per target
        Map<LogicalTarget, Dataset> datasetsByTarget = processor.executeToDatasets(doc);
        assertThat(datasetsByTarget).isNotEmpty();
        assertThat(datasetsByTarget).containsKey(LogicalTarget.DEFAULT);

        Dataset defaultDs = datasetsByTarget.get(LogicalTarget.DEFAULT);
        assertThat(defaultDs.getDefaultModel().size()).isGreaterThan(0);

        // 3. Streaming push execution
        List<RdfStatement> streamed = new ArrayList<>();
        processor.execute(doc, (target, stmt) -> {
            assertThat(target).isNotNull();
            streamed.add(stmt);
        });
        assertThat(streamed).hasSameSizeAs(defaultStmts);
    }

    @Test
    public void testCollectionAndContainerLineageAndTargetsPreserved() throws Exception {
        Path mappingPath = Path.of("rml-modules/rml-cc/test-cases/RMLTC-CC-0001-List/mapping.ttl");
        RmlLoader loader = new RmlLoader(mappingPath);
        MappingDocument doc = loader.loadMappingDocument();

        RmlProcessor processor = new RmlProcessor();
        ExecutionOptions options = ExecutionOptions.builder().baseIri("http://example.com/base/").build();
        Map<LogicalTarget, List<RdfStatement>> stmtsByTarget = processor.executeToStatements(doc, options);

        assertThat(stmtsByTarget).containsKey(LogicalTarget.DEFAULT);
        List<RdfStatement> stmts = stmtsByTarget.get(LogicalTarget.DEFAULT);
        assertThat(stmts).isNotEmpty();

        // Check that all statements (including rdf:first, rdf:rest) have lineage preserved
        for (RdfStatement stmt : stmts) {
            assertThat(stmt.lineage()).as("Lineage should be preserved for statement: %s", stmt).isNotNull();
            assertThat(stmt.lineage().primary()).isNotNull();
        }
    }
}
