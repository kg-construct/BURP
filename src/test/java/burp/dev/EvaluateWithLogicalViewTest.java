package burp.dev;

import burp.dev.rml.RmlDevServerImpl;
import burp.dev.rml.RmlLanguageServer;
import burp.dev.rml.service.eval.AllDataContext;
import burp.dev.rml.service.eval.EvaluateParams;
import burp.dev.rml.service.eval.EvaluateResult;
import burp.dev.rml.service.selector.CursorSelector;
import burp.dev.rml.service.selector.IriSelector;
import burp.model.RawValue;
import burp.model.RmlEvaluationResult;
import burp.model.rdf.IRITerm;
import burp.model.rdf.LiteralTerm;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class EvaluateWithLogicalViewTest {

    @Test
    public void testEvalLogicalViewSubjectMap() throws ExecutionException, InterruptedException {
        // rml:subjectMap [|
        EvaluateParams params = new EvaluateParams(new CursorSelector("src/test/resources/burp/eval_logicalview/mapping.ttl", new Position(68, 10)), new AllDataContext());

        RmlLanguageServer server = new RmlDevServerImpl();
        List<EvaluateResult> results = server.evaluate(params).get();

        assertThat(results).isNotNull().hasSize(2);

        List<RmlEvaluationResult> rdfResults0 = results.getFirst().generatedRdf();
        assertThat(rdfResults0).hasSize(1);
        IRITerm subjectTerm0 = assertInstanceOf(IRITerm.class, rdfResults0.getFirst());
        assertThat(subjectTerm0.uri()).isEqualTo("http://example.org/person/1");

        List<RmlEvaluationResult> rdfResults1 = results.get(1).generatedRdf();
        assertThat(rdfResults1).hasSize(1);
        IRITerm subjectTerm1 = assertInstanceOf(IRITerm.class, rdfResults1.getFirst());
        assertThat(subjectTerm1.uri()).isEqualTo("http://example.org/person/1");
    }

    @Test
    public void testEvalLogicalViewJoinField() throws ExecutionException, InterruptedException {
        // rml:reference "item|_type"
        EvaluateParams params = new EvaluateParams(new CursorSelector("src/test/resources/burp/eval_logicalview/mapping.ttl", new Position(88, 25)), new AllDataContext());

        RmlLanguageServer server = new RmlDevServerImpl();
        List<EvaluateResult> results = server.evaluate(params).get();

        assertThat(results).isNotNull().hasSize(2);

        List<RmlEvaluationResult> rdfResults0 = results.getFirst().generatedRdf();
        assertThat(rdfResults0).hasSize(1);
        LiteralTerm item1 = assertInstanceOf(LiteralTerm.class, rdfResults0.getFirst());
        assertThat(item1.value()).isEqualTo("Book");

        List<RmlEvaluationResult> rdfResults1 = results.get(1).generatedRdf();
        assertThat(rdfResults1).hasSize(1);
        LiteralTerm item2 = assertInstanceOf(LiteralTerm.class, rdfResults1.getFirst());
        assertThat(item2.value()).isEqualTo("Laptop");
    }

    @Test
    public void testEvaluateLogicalSourceAndViewByIri() throws ExecutionException, InterruptedException {
        RmlDevServerImpl server = new RmlDevServerImpl();
        String mappingUri = "src/test/resources/burp/eval_logicalview/mapping.ttl";

        List<EvaluateResult> jsonSourceRes = server.evaluate(new EvaluateParams(new IriSelector(mappingUri, "http://example.org/jsonSource"), new AllDataContext())).get();

        assertThat(jsonSourceRes).hasSize(1);
        assertThat(jsonSourceRes)
                .flatMap(EvaluateResult::generatedRdf)
                .hasSize(1)
                .anySatisfy(val -> assertThat(((RawValue) val).value().toString()).contains("Alice"));

        List<EvaluateResult> jsonViewRes = server.evaluate(new EvaluateParams(new IriSelector(mappingUri, "http://example.org/jsonView"), new AllDataContext())).get();

        assertThat(jsonViewRes).hasSize(2);
        assertThat(jsonViewRes)
                .flatMap(EvaluateResult::generatedRdf)
                .hasSize(2)
                .anySatisfy(val -> assertThat(((RawValue) val).value().toString()).contains("Alice"));
    }

    @Test
    public void testEvaluateElsdvlee20IterableAndExpressionFields() throws ExecutionException, InterruptedException {
        RmlDevServerImpl server = new RmlDevServerImpl();
        server.getWorkspace().setCurrentWorkingDirectory(java.nio.file.Path.of("src/test/resources/burp/ELSDVLEE-20").toAbsolutePath());
        String mappingUri = "mapping.ttl";

        // IterableField "item" (line 20: rml:fieldName "item")
        List<EvaluateResult> iterFieldRes = server.evaluate(new EvaluateParams(
                new CursorSelector(mappingUri, new Position(20, 18)),
                new AllDataContext()
        )).get();
        assertThat(iterFieldRes).hasSize(3);
        assertThat(iterFieldRes)
                .flatMap(EvaluateResult::generatedRdf)
                .extracting(r -> ((RawValue) r).value().toString())
                .anySatisfy(s -> assertThat(s).contains("sword"))
                .anySatisfy(s -> assertThat(s).contains("shield"))
                .anySatisfy(s -> assertThat(s).contains("flower"));

        // Nested ExpressionField "type" inside IterableField "item" (line 24: rml:fieldName "type")
        List<EvaluateResult> typeExprFieldRes = server.evaluate(new EvaluateParams(
                new CursorSelector(mappingUri, new Position(24, 18)),
                new AllDataContext()
        )).get();
        assertThat(typeExprFieldRes).hasSize(3);
        assertThat(typeExprFieldRes)
                .flatMap(EvaluateResult::generatedRdf)
                .extracting(r -> ((RawValue) r).value())
                .containsExactly("sword", "shield", "flower");

        // Nested RawReference "$.type" inside IterableField "item" (line 25: rml:reference "$.type")
        List<EvaluateResult> typeRefRes = server.evaluate(new EvaluateParams(
                new CursorSelector(mappingUri, new Position(25, 22)),
                new AllDataContext()
        )).get();
        assertThat(typeRefRes).hasSize(3);
        assertThat(typeRefRes)
                .flatMap(EvaluateResult::generatedRdf)
                .extracting(r -> ((LiteralTerm) r).value())
                .containsExactly("sword", "shield", "flower");

        // Nested RawReference "$.weight" inside IterableField "item" (line 30: rml:reference "$.weight")
        List<EvaluateResult> weightRefRes = server.evaluate(new EvaluateParams(
                new CursorSelector(mappingUri, new Position(30, 22)),
                new AllDataContext()
        )).get();
        assertThat(weightRefRes).hasSize(3);
        assertThat(weightRefRes)
                .flatMap(EvaluateResult::generatedRdf)
                .extracting(r -> ((LiteralTerm) r).value())
                .containsExactly("1500", "2500", "15");

        // LeftJoin RawReference "item.type" on parentLogicalView :jsonView (line 65: rml:reference "item.type")
        List<EvaluateResult> joinFieldRes = server.evaluate(new EvaluateParams(
                new CursorSelector(mappingUri, new Position(65, 22)),
                new AllDataContext()
        )).get();
        assertThat(joinFieldRes).hasSize(3);
        assertThat(joinFieldRes)
                .flatMap(EvaluateResult::generatedRdf)
                .extracting(r -> ((LiteralTerm) r).value())
                .containsExactly("sword", "shield", "flower");
    }
}

