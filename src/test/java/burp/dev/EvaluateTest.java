package burp.dev;

import burp.dev.rml.RmlDevServerImpl;
import burp.dev.rml.RmlLanguageServer;
import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.eval.*;
import burp.dev.rml.service.selector.CursorSelector;
import burp.dev.rml.service.selector.IriSelector;
import burp.model.*;
import burp.model.context.EvaluationContext;
import burp.model.rdf.IRITerm;
import burp.model.rdf.LiteralTerm;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class EvaluateTest {

    private void assertEvaluateResult(
            EvaluateResult result,
            String expectedId,
            String expectedName,
            String expectedAge
    ) {
        assertThat(result.iterationId()).isEqualTo(expectedId);
        assertThat(result.iterationData()).contains(expectedName, expectedAge);

        List<RmlEvaluationResult> rdfResults = result.generatedRdf();
        assertThat(rdfResults).hasSize(1);
        RdfStatement stmt = assertInstanceOf(RdfStatement.class, rdfResults.getFirst());

        IRITerm subject = assertInstanceOf(IRITerm.class, stmt.getSubject());
        assertThat(subject.uri()).isEqualTo("http://example.org/person/" + expectedName);

        IRITerm predicate = assertInstanceOf(IRITerm.class, stmt.getPredicate());
        assertThat(predicate.uri()).isEqualTo("http://example.org/age");

        LiteralTerm object = assertInstanceOf(LiteralTerm.class, stmt.getObject());
        assertThat(object.value()).isEqualTo(expectedAge);
    }

    @Test
    public void testEvalAllData() throws ExecutionException, InterruptedException {
        EvaluateParams params = new EvaluateParams(
                new CursorSelector("src/test/resources/burp/eval_alice/mapping.ttl", new Position(3, 1)),
                new AllDataContext()
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        List<EvaluateResult> results = server.evaluate(params).get();

        assertThat(results).isNotNull().hasSize(3);
        assertEvaluateResult(results.get(0), "0", "Alice", "30");
        assertEvaluateResult(results.get(1), "1", "Bob", "25");
        assertEvaluateResult(results.get(2), "2", "Charlie", "40");
    }

    @Test
    public void testEvalRangeData() throws ExecutionException, InterruptedException {
        EvaluateParams params = new EvaluateParams(
                new CursorSelector("src/test/resources/burp/eval_alice/mapping.ttl", new Position(3, 1)),
                new RangeDataContext(0, 1)
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        List<EvaluateResult> results = server.evaluate(params).get();

        assertThat(results).isNotNull().hasSize(2);
        assertEvaluateResult(results.get(0), "0", "Alice", "30");
        assertEvaluateResult(results.get(1), "1", "Bob", "25");
    }

    @Test
    public void testEvaluateResult_IncludesTargetNodeMetadata() throws ExecutionException, InterruptedException {
        EvaluateParams params = new EvaluateParams(
                new CursorSelector("src/test/resources/burp/eval_alice/mapping.ttl", new Position(3, 1)),
                new RangeDataContext(0, 1)
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        List<EvaluateResult> results = server.evaluate(params).get();

        assertThat(results).isNotNull().isNotEmpty();
        EvaluateResult first = results.getFirst();
        assertThat(first.targetNodeType()).isEqualTo("TriplesMap");
        assertThat(first.targetParentTriplesMap()).contains("TriplesMap");
        assertThat(first.evaluationMode()).isEqualTo(burp.dev.rml.service.hint.InlineHintsEvaluationMode.TRACE);
    }

    @Test
    public void testEvalCustomMapData() throws ExecutionException, InterruptedException {
        Map<String, Object> referenceValues = new HashMap<>();
        referenceValues.put("$.name", "Dan");
        referenceValues.put("$.age", 35);

        EvaluateParams params = new EvaluateParams(
                new CursorSelector("src/test/resources/burp/eval_alice/mapping.ttl", new Position(3, 1)),
                new ReferenceValuesCustomDataContext(referenceValues)
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        List<EvaluateResult> results = server.evaluate(params).get();

        assertThat(results).isNotNull().hasSize(1);
        assertEvaluateResult(results.getFirst(), "0", "Dan", "35");
    }

    @Test
    public void testEvalCustomStringData() throws ExecutionException, InterruptedException {
        EvaluateParams params = new EvaluateParams(
                new CursorSelector("src/test/resources/burp/eval_alice/mapping.ttl", new Position(3, 1)),
                new RawStringCustomDataContext("""
                        {"name": "Eva", "age": 50}""")
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        List<EvaluateResult> results = server.evaluate(params).get();

        assertThat(results).isNotNull().hasSize(1);
        assertEvaluateResult(results.getFirst(), "0", "Eva", "50");
    }

    @Test
    public void testEvalCursorOnObjectMap() throws ExecutionException, InterruptedException {
        // Cursor on line 19, column 28: inside the objectMap rml:reference "$.age"
        // Only the age Term should be generated (not a full statement).
        EvaluateParams params = new EvaluateParams(
                new CursorSelector("src/test/resources/burp/eval_alice/mapping.ttl", new Position(18, 28)),
                new ReferenceValuesCustomDataContext(Map.of("$.name", "Alice", "$.age", 30))
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        List<EvaluateResult> results = server.evaluate(params).get();

        assertThat(results).isNotNull().hasSize(1);
        List<RmlEvaluationResult> rdfResults = results.getFirst().generatedRdf();
        assertThat(rdfResults).hasSize(1);

        // The objectMap resolves to the literal value of $.age
        LiteralTerm ageTerm = assertInstanceOf(LiteralTerm.class, rdfResults.getFirst());
        // TODO: JSONPath natural datatype conversion (integers should map to xsd:integer, not plain string)
        assertThat(ageTerm.value()).isEqualTo("30");
    }

    @Test
    public void testEvalCursorOnSubjectMap() throws ExecutionException, InterruptedException {
        // Cursor on line 13 (0-based 12), column 5: on rml:subjectMap [
        EvaluateParams params = new EvaluateParams(
                new CursorSelector("src/test/resources/burp/eval_alice/mapping.ttl", new Position(12, 5)),
                new ReferenceValuesCustomDataContext(Map.of("$.name", "Alice", "$.age", 30))
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        List<EvaluateResult> results = server.evaluate(params).get();

        assertThat(results).isNotNull().hasSize(1);
        List<RmlEvaluationResult> rdfResults = results.getFirst().generatedRdf();
        assertThat(rdfResults).hasSize(1);

        // The subjectMap resolves to the IRI generated from the template
        IRITerm subjectTerm = assertInstanceOf(IRITerm.class, rdfResults.getFirst());
        assertThat(subjectTerm.uri()).isEqualTo("http://example.org/person/Alice");
    }

    @Test
    public void testEvalCursorOnReferenceSegment() throws ExecutionException, InterruptedException {
        // Cursor on line 14 (0-based 13), column 50: inside {$.name}
        EvaluateParams params = new EvaluateParams(
                new CursorSelector("src/test/resources/burp/eval_alice/mapping.ttl", new Position(13, 50)),
                new ReferenceValuesCustomDataContext(Map.of("$.name", "Alice", "$.age", 30))
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        List<EvaluateResult> results = server.evaluate(params).get();

        assertThat(results).isNotNull().hasSize(1);
        List<RmlEvaluationResult> rdfResults = results.getFirst().generatedRdf();
        assertThat(rdfResults).hasSize(1);

        LiteralTerm nameTerm = assertInstanceOf(LiteralTerm.class, rdfResults.getFirst());
        assertThat(nameTerm.value()).isEqualTo("Alice");
    }

    @Test
    public void testEvalFnmlCondition() throws ExecutionException, InterruptedException {
        RmlLanguageServer server = new RmlDevServerImpl();

        // 1. Template evaluation at line 30 (0-based)
        EvaluateParams templateParams = new EvaluateParams(
                new CursorSelector("src/test/resources/burp/trace_fnml_condition/mapping.ttl", new Position(30, 20)),
                new RangeDataContext(0, 0)
        );
        List<EvaluateResult> templateResults = server.evaluate(templateParams).get();
        assertThat(templateResults).isNotNull().isNotEmpty();

        // 2. Reference evaluation at line 36 (0-based 35)
        EvaluateParams refParams = new EvaluateParams(
                new CursorSelector("src/test/resources/burp/trace_fnml_condition/mapping.ttl", new Position(35, 20)),
                new RangeDataContext(0, 0)
        );
        List<EvaluateResult> refResults = server.evaluate(refParams).get();
        assertThat(refResults).isNotNull().isNotEmpty();

        // 3. FunctionExecution evaluation at line 17 (0-based 16)
        EvaluateParams feParams = new EvaluateParams(
                new CursorSelector("src/test/resources/burp/trace_fnml_condition/mapping.ttl", new Position(16, 20)),
                new RangeDataContext(0, 0)
        );
        List<EvaluateResult> feResults = server.evaluate(feParams).get();
        assertThat(feResults).isNotNull().isNotEmpty();
    }

    @Test
    public void testEvaluateJoinAndDependentNodesWithBothLogicalSourcesOverridden() throws ExecutionException, InterruptedException {
        String mappingUri = "src/test/resources/burp/rml_tc0009a/mapping.ttl";
        RmlLanguageServer server = new RmlDevServerImpl();

        // 1. Override both student.json (child) and sport.json (parent)
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

        // A. Evaluate the join itself: ReferencingObjectMap using its IRI
        EvaluateParams refObjectMapParams = new EvaluateParams(
                new IriSelector(mappingUri, "http://example.com/base/JoinRefObjectMap"),
                multiCtx
        );
        List<EvaluateResult> romResults = server.evaluate(refObjectMapParams).get();
        assertThat(romResults).isNotNull().hasSize(1);
        assertThat(romResults.getFirst().generatedRdf()).hasSize(1);
        IRITerm sportTerm = assertInstanceOf(IRITerm.class, romResults.getFirst().generatedRdf().getFirst());
        assertThat(sportTerm.uri()).isEqualTo("http://example.com/resource/sport_999");

        // B. Evaluate node depending on that join: PredicateObjectMap using its IRI
        EvaluateParams pomParams = new EvaluateParams(
                new IriSelector(mappingUri, "http://example.com/base/JoinPredicateObjectMap"),
                multiCtx
        );
        List<EvaluateResult> pomResults = server.evaluate(pomParams).get();
        assertThat(pomResults).isNotNull().hasSize(1);
        assertThat(pomResults.getFirst().generatedRdf()).hasSize(1);
        RdfPredicateObject pomResult = assertInstanceOf(RdfPredicateObject.class, pomResults.getFirst().generatedRdf().getFirst());
        assertThat(pomResult.predicate().uri()).isEqualTo("http://example.com/ontology/practises");
        assertThat(assertInstanceOf(IRITerm.class, pomResult.object()).uri()).isEqualTo("http://example.com/resource/sport_999");

        // C. Evaluate top-level TriplesMap depending on that join (IriSelector for TriplesMap1)
        EvaluateParams tmParams = new EvaluateParams(
                new IriSelector(mappingUri, "http://example.com/base/TriplesMap1"),
                multiCtx
        );
        List<EvaluateResult> tmResults = server.evaluate(tmParams).get();
        assertThat(tmResults).isNotNull().hasSize(1);
        List<RmlEvaluationResult> tmStatements = tmResults.getFirst().generatedRdf();
        assertThat(tmStatements).hasSize(2);

        // Verify statement 1: name
        assertThat(tmStatements).anyMatch(r -> r instanceof RdfStatement s &&
                s.getSubject() instanceof IRITerm sub && sub.uri().equals("http://example.com/resource/student_99") &&
                s.getPredicate() instanceof IRITerm pred && pred.uri().equals("http://xmlns.com/foaf/0.1/name") &&
                s.getObject() instanceof LiteralTerm obj && obj.value().equals("Custom Player")
        );

        // Verify statement 2: join result
        assertThat(tmStatements).anyMatch(r -> r instanceof RdfStatement s &&
                s.getSubject() instanceof IRITerm sub && sub.uri().equals("http://example.com/resource/student_99") &&
                s.getPredicate() instanceof IRITerm pred && pred.uri().equals("http://example.com/ontology/practises") &&
                s.getObject() instanceof IRITerm obj && obj.uri().equals("http://example.com/resource/sport_999")
        );
    }

    @Test
    public void testIntermediateMockingViaNodeOverrides() throws Exception {
        String uri = "src/test/resources/burp/eval_alice/mapping.ttl";
        MappingWorkspace workspace = new MappingWorkspace();
        var document = workspace.loadMappingWithStableIds(uri);
        assertThat(document.triplesMaps).isNotEmpty();

        PlanNode targetNode = document.triplesMaps.getFirst().subjectMap;
        assertThat(targetNode).isNotNull();
        String nodeId = PlanNodeIdRegistry.getId(targetNode);

        RmlEvaluator evaluator = new RmlEvaluator();
        EvaluationContext baseContext = EvaluationContext.createDefault();

        // Inject mock result for the subject map node ID
        IRITerm mockTerm = new IRITerm("http://mocked.example.org/overridden_alice");
        EvaluationContext mockedContext = baseContext.withNodeOverrides(Map.of(nodeId, List.of(mockTerm)));

        List<RmlEvaluationResult> results = evaluator.evaluateNodeSafe(targetNode, null, mockedContext);
        assertThat(results)
                .hasSize(1)
                .first()
                .isEqualTo(mockTerm);
    }

    @Test
    public void testTemplateAndTermMapPolymorphicEvaluation() throws Exception {
        String uri = "src/test/resources/burp/eval_alice/mapping.ttl";
        MappingWorkspace workspace = new MappingWorkspace();
        MappingDocument document = workspace.loadMappingWithStableIds(uri);
        TriplesMap tm = document.triplesMaps.getFirst();

        RmlEvaluator evaluator = new RmlEvaluator();
        List<Iteration> iterations = evaluator.resolveIterations(new AllDataContext(), tm);
        assertThat(iterations).isNotEmpty();
        Iteration firstIter = iterations.getFirst();

        // 1. TermMap (SubjectMap) generates typed RDF term (IRITerm)
        List<RmlEvaluationResult> smResults = tm.subjectMap.evaluateNode(firstIter, EvaluationContext.createDefault());
        assertThat(smResults).hasSize(1);
        assertThat(smResults.getFirst()).isInstanceOf(IRITerm.class);
        assertThat(((IRITerm) smResults.getFirst()).uri()).isEqualTo("http://example.org/person/Alice");

        // 2. Template expression evaluates to raw interpolated value (RawValue), not IRITerm
        Template tpl = assertInstanceOf(Template.class, tm.subjectMap.expression);
        List<RmlEvaluationResult> tplResults = tpl.evaluateNode(firstIter, EvaluationContext.createDefault());
        assertThat(tplResults).hasSize(1);
        assertThat(tplResults.getFirst()).isInstanceOf(RawValue.class);
        assertThat(((RawValue) tplResults.getFirst()).value()).isEqualTo("http://example.org/person/Alice");
    }

    @Test
    public void testPredicateObjectMapEvaluatesToPredicateObject() throws Exception {
        String uri = "src/test/resources/burp/eval_alice/mapping.ttl";
        MappingWorkspace workspace = new MappingWorkspace();
        MappingDocument document = workspace.loadMappingWithStableIds(uri);
        TriplesMap tm = document.triplesMaps.getFirst();

        RmlEvaluator evaluator = new RmlEvaluator();
        List<Iteration> iterations = evaluator.resolveIterations(new AllDataContext(), tm);
        assertThat(iterations).isNotEmpty();
        Iteration firstIter = iterations.getFirst();

        PredicateObjectMap pom = tm.predicateObjectMaps.getFirst();
        List<RmlEvaluationResult> pomResults = pom.evaluateNode(firstIter, EvaluationContext.createDefault());
        assertThat(pomResults).isNotEmpty();
        assertThat(pomResults.getFirst()).isInstanceOf(RdfPredicateObject.class);

        RdfPredicateObject po = (RdfPredicateObject) pomResults.getFirst();
        assertThat(po.predicate().uri()).isEqualTo("http://example.org/age");
        assertThat(po.object()).isInstanceOf(LiteralTerm.class);
        assertThat(((LiteralTerm) po.object()).value()).isEqualTo("30");
    }
}
