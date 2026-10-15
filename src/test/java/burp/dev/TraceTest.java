package burp.dev;

import burp.dev.rml.RmlDevServerImpl;
import burp.dev.rml.RmlLanguageServer;
import burp.dev.rml.service.eval.AllDataContext;
import burp.dev.rml.service.selector.CursorSelector;
import burp.dev.rml.service.selector.IriSelector;
import burp.dev.rml.service.trace.*;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

public class TraceTest {

    @Test
    public void testTraceAllData() throws ExecutionException, InterruptedException {
        TraceParams params = new TraceParams(
                // Cursor in the Logical Source
                new CursorSelector("src/test/resources/burp/eval_alice/mapping.ttl", new Position(6, 1)),
                new AllDataContext()
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        TraceResult result = server.trace(params).get();

        assertThat(result).isNotNull();
        List<TraceNode> nodes = result.nodes();
        assertThat(nodes).isNotNull().isNotEmpty();

        // Check that we have a TriplesMap node
        TraceNode tmNode = nodes.stream()
                .filter(n -> "TriplesMap".equals(n.nodeType()))
                .findFirst()
                .orElse(null);
        assertThat(tmNode).isNotNull();
        assertThat(tmNode.outputs()).isNotEmpty();

        // Check JSON-LD graph representation
        String jsonLd = result.graphJsonLD();
        assertThat(jsonLd)
                .isNotNull()
                .contains("TraceNode", "http://w3id.org/rml/reports/");
    }

    @Test
    public void testTraceCursorOnObjectMap() throws ExecutionException, InterruptedException {
        TraceParams params = new TraceParams(
                // Cursor on line 19, column 30: inside rml:reference "$.age"
                new CursorSelector("src/test/resources/burp/eval_alice/mapping.ttl", new Position(18, 30)),
                new AllDataContext()
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        TraceResult result = server.trace(params).get();

        assertThat(result).isNotNull();
        List<TraceNode> nodes = result.nodes();
        assertThat(nodes).isNotNull().isNotEmpty();

        TraceNode objectMapNode = nodes.stream()
                .filter(n -> "ObjectMap".equals(n.nodeType()))
                .findFirst()
                .orElse(null);
        assertThat(objectMapNode)
                .as("ObjectMap should be present")
                .isNotNull();
        assertThat(objectMapNode.outputs()).isNotEmpty();
        assertThat(objectMapNode.outputs())
                .as("Outputs should contain age '30'")
                .anyMatch(v -> v.text().contains("30"));

        String jsonLd = result.graphJsonLD();
        assertThat(jsonLd)
                .isNotNull()
                .contains("TraceNode", "TraceValue");
    }

    @Test
    public void testTraceTemplateWithNullReferenceSegment() throws ExecutionException, InterruptedException {
        TraceParams params = new TraceParams(
                new CursorSelector("src/test/resources/burp/trace_template_null/mapping.ttl", new Position(12, 1)),
                new AllDataContext()
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        TraceResult result = server.trace(params).get();

        assertThat(result).isNotNull();
        List<TraceNode> nodes = result.nodes();
        assertThat(nodes).isNotNull();
        var subjectMap = nodes.stream().filter(n -> n.nodeType().equals("SubjectMap")).findFirst().orElse(null);
        assertThat(subjectMap).isNotNull();
        assertThat(subjectMap.outputs()).hasSize(1);

        var template = nodes.stream().filter(n -> n.nodeType().equals("Template")).findFirst().orElse(null);
        assertThat(template).isNotNull();
        assertThat(subjectMap.dependencyIds())
                .as("SubjectMap should depend on Template")
                .contains(template.nodeId());

        // Template only produced 1 output (for Alice_Smith) because Bob had a null lastName
        assertThat(template.outputs()).hasSize(1);
        TraceValue templateOutput = template.outputs().getFirst();
        assertThat(templateOutput.text()).isEqualTo("http://example.org/person/Alice_Smith");

        var referenceSegments = nodes.stream().filter(n -> n.nodeType().equals("ReferenceSegment")).toList();
        assertThat(referenceSegments).hasSize(2);

        TraceNode referenceSegmentFirstName = referenceSegments.get(0);
        TraceNode referenceSegmentLastName = referenceSegments.get(1);
        assertThat(template.dependencyIds())
                .as("Template should depend on its ReferenceSegment")
                .contains(referenceSegmentFirstName.nodeId(), referenceSegmentLastName.nodeId());

        // FirstName produced 2 values: Alice (iter 0) and Bob (iter 1)
        assertThat(referenceSegmentFirstName.outputs()).hasSize(2);
        assertThat(referenceSegmentFirstName.outputs().get(0).text()).contains("Alice");
        assertThat(referenceSegmentFirstName.outputs().get(1).text()).contains("Bob");

        // LastName produced 1 value: Smith (iter 0)
        assertThat(referenceSegmentLastName.outputs()).hasSize(1);
        assertThat(referenceSegmentLastName.outputs().getFirst().text()).contains("Smith");

        // Verify fine-grained data-flow links:
        // Template output derived from "Alice" (firstName) and "Smith" (lastName)
        TraceValue aliceVal = referenceSegmentFirstName.outputs().getFirst();
        TraceValue smithVal = referenceSegmentLastName.outputs().getFirst();
        assertThat(templateOutput.derivedFromValueIds())
                .as("Template should derive from Alice value")
                .contains(aliceVal.valueId());
        assertThat(templateOutput.derivedFromValueIds())
                .as("Template should derive from Smith value")
                .contains(smithVal.valueId());

        // SubjectMap output derived from Template output
        TraceValue subjectMapOutput = subjectMap.outputs().getFirst();
        assertThat(subjectMapOutput.derivedFromValueIds())
                .as("SubjectMap term should derive from Template value")
                .contains(templateOutput.valueId());

        String jsonLd = result.graphJsonLD();
        assertThat(jsonLd)
                .isNotNull()
                .contains("TraceNode", "TraceValue", "wasDerivedFrom");
    }

    @Test
    public void testTraceWithFnmlCondition() throws ExecutionException, InterruptedException {
        TraceParams params = new TraceParams(
                new CursorSelector("src/test/resources/burp/trace_fnml_condition/mapping.ttl", new Position(12, 1)),
                new AllDataContext()
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        TraceResult result = server.trace(params).get();

        assertThat(result).isNotNull();
        List<TraceNode> nodes = result.nodes();
        assertThat(nodes).isNotNull().isNotEmpty();

        // In trace_fnml_condition:
        // Alice has role 'admin' -> passes condition -> SubjectMap generates term
        // Bob has role 'user' -> fails condition -> SubjectMap produces no term
        TraceNode subjectMap = nodes.stream()
                .filter(n -> "SubjectMap".equals(n.nodeType()))
                .findFirst()
                .orElse(null);
        assertThat(subjectMap)
                .as("SubjectMap should be present")
                .isNotNull();
        assertThat(subjectMap.outputs())
                .as("Only 1 subject term should be produced (for admin Alice)")
                .hasSize(1);
        assertThat(subjectMap.outputs().getFirst().text()).contains("admin/Alice");

        // TriplesMap statements produced only for Alice
        TraceNode tmNode = nodes.stream()
                .filter(n -> "TriplesMap".equals(n.nodeType()))
                .findFirst()
                .orElse(null);
        assertThat(tmNode)
                .as("TriplesMap should be present")
                .isNotNull();
        assertThat(tmNode.outputs())
                .as("Only 1 statement should be produced")
                .hasSize(1);

        String jsonLd = result.graphJsonLD();
        assertThat(jsonLd)
                .isNotNull()
                .contains("TraceNode");
    }

    @Test
    public void testTraceWithJoinCondition() throws ExecutionException, InterruptedException {
        TraceParams params = new TraceParams(
                new CursorSelector("src/test/resources/burp/trace_join_condition/mapping.ttl", new Position(12, 1)),
                new AllDataContext()
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        TraceResult result = server.trace(params).get();

        assertThat(result).isNotNull();
        List<TraceNode> nodes = result.nodes();
        assertThat(nodes).isNotNull().isNotEmpty();

        // Parent triples map is included in the trace graph
        boolean hasParentMapping = nodes.stream().anyMatch(n -> "ParentMapping".equals(n.nodeId()) || n.nodeId().contains("Parent"));
        assertThat(hasParentMapping || nodes.stream().anyMatch(n -> "ReferencingObjectMap".equals(n.nodeType())))
                .as("Join nodes and parent mappings should be included in trace")
                .isTrue();

        // Alice (deptId=dept1) matches parent dept1 (Engineering); Bob (deptId=dept3) has no match
        TraceNode childTm = nodes.stream()
                .filter(n -> "TriplesMap".equals(n.nodeType()) && !"ParentMapping".equals(n.nodeId()))
                .findFirst()
                .orElse(null);
        assertThat(childTm)
                .as("Child TriplesMap should be present")
                .isNotNull();
        assertThat(childTm.outputs())
                .as("Only matching child iteration (Alice) should produce statement")
                .hasSize(1);

        String jsonLd = result.graphJsonLD();
        assertThat(jsonLd)
                .isNotNull()
                .contains("TraceNode");
    }

    @Test
    public void testTraceWithRmlCcNamedListAppendedAcrossTriplesMaps() throws ExecutionException, InterruptedException {
        TraceParams params = new TraceParams(
                new CursorSelector("src/test/resources/burp/trace_cc_append/mapping.ttl", new Position(12, 1)),
                new AllDataContext()
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        TraceResult result = server.trace(params).get();

        assertThat(result).isNotNull();
        List<TraceNode> nodes = result.nodes();
        assertThat(nodes).isNotNull().isNotEmpty();

        String jsonLd = result.graphJsonLD();
        assertThat(jsonLd)
                .isNotNull()
                .contains("TraceNode");
    }

    @Test
    public void testTraceWithNestedLogicalViewCursorInTriplesMap() throws ExecutionException, InterruptedException {
        TraceParams params = new TraceParams(
                // Cursor at line 29 (Position 28, 1): :triplesMapPerson TriplesMap
                new CursorSelector("rml-modules/rml-lv/test-cases/RMLLVTC0000c/mapping.ttl", new Position(28, 1)),
                new AllDataContext()
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        TraceResult result = server.trace(params).get();

        assertThat(result).isNotNull();
        List<TraceNode> nodes = result.nodes();
        assertThat(nodes).isNotNull().isNotEmpty();

        // TriplesMap should generate statements for alice and bob
        TraceNode tmNode = nodes.stream()
                .filter(n -> "TriplesMap".equals(n.nodeType()))
                .findFirst()
                .orElse(null);
        assertThat(tmNode)
                .as("TriplesMap should be present")
                .isNotNull();
        assertThat(tmNode.outputs())
                .as("Should produce 2 statements for alice and bob")
                .hasSize(2);

        // The trace should include the LogicalViews and LogicalSource
        assertThat(nodes)
                .as("LogicalView should be included in trace nodes")
                .extracting(TraceNode::nodeType)
                .contains("LogicalView");

        String jsonLd = result.graphJsonLD();
        assertThat(jsonLd)
                .isNotNull()
                .contains("TraceNode");
    }

    @Test
    public void testTraceWithNestedLogicalViewCursorInSecondView() throws ExecutionException, InterruptedException {
        TraceParams params = new TraceParams(
                // Cursor at line 21 (Position 20, 1): :jsonViewOnJsonView LogicalView
                new CursorSelector("rml-modules/rml-lv/test-cases/RMLLVTC0000c/mapping.ttl", new Position(20, 1)),
                new AllDataContext()
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        TraceResult result = server.trace(params).get();

        assertThat(result).isNotNull();
        List<TraceNode> nodes = result.nodes();
        assertThat(nodes).isNotNull().isNotEmpty();

        // Verify that LogicalViews are traced
        TraceNode viewNode = nodes.stream()
                .filter(n -> "LogicalView".equals(n.nodeType()))
                .findFirst()
                .orElse(null);
        assertThat(viewNode)
                .as("LogicalView node should be present")
                .isNotNull();
        assertThat(viewNode.outputs())
                .as("LogicalView should have outputs for records")
                .isNotEmpty();

        String jsonLd = result.graphJsonLD();
        assertThat(jsonLd)
                .isNotNull()
                .contains("TraceNode");
    }

    @Test
    public void testTraceWithFunctionExecution() throws ExecutionException, InterruptedException {
        TraceParams params = new TraceParams(
                new CursorSelector("src/test/resources/burp/BURPFNMLTC-CSV-Concat/mapping.ttl", new Position(15, 10)),
                new AllDataContext()
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        TraceResult result = server.trace(params).get();

        assertThat(result).isNotNull();
        List<TraceNode> nodes = result.nodes();
        assertThat(nodes).isNotNull().isNotEmpty();

        // 1. Verify FunctionExecution is traced
        TraceNode fnNode = nodes.stream()
                .filter(n -> "FunctionExecution".equals(n.nodeType()))
                .findFirst()
                .orElse(null);
        assertThat(fnNode).as("FunctionExecution should be traced").isNotNull();
        assertThat(fnNode.label()).as("FunctionExecution should have a descriptive label").isNotNull();
        assertThat(fnNode.outputs()).as("FunctionExecution should produce outputs").isNotEmpty();
        assertThat(fnNode.outputs().getFirst().text()).contains("Gaston - A");

        // 2. Verify topological order of nodes in result
        int lsIdx = -1;
        int refIdx = -1;
        int fnIdx = -1;
        int pomIdx = -1;
        int tmIdx = -1;

        for (int i = 0; i < nodes.size(); i++) {
            String type = nodes.get(i).nodeType();
            if (lsIdx == -1 && type.contains("LogicalSource")) lsIdx = i;
            if (refIdx == -1 && (type.contains("Reference") || type.contains("ReferenceSegment"))) refIdx = i;
            if (fnIdx == -1 && "FunctionExecution".equals(type)) fnIdx = i;
            if (pomIdx == -1 && "PredicateObjectMap".equals(type)) pomIdx = i;
            if (tmIdx == -1 && "TriplesMap".equals(type)) tmIdx = i;
        }

        assertThat(lsIdx).as("LogicalSource should precede Reference").isLessThan(refIdx);
        assertThat(refIdx).as("Reference should precede FunctionExecution").isLessThan(fnIdx);
        assertThat(fnIdx).as("FunctionExecution should precede PredicateObjectMap").isLessThan(pomIdx);
        assertThat(pomIdx).as("PredicateObjectMap should precede TriplesMap").isLessThan(tmIdx);
    }

    @Test
    public void testTraceSingleIteration() throws ExecutionException, InterruptedException {
        TraceIterationParams params = new TraceIterationParams(
                new CursorSelector("src/test/resources/burp/eval_alice/mapping.ttl", new Position(6, 1)),
                0
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        TraceResult result = server.traceIteration(params).get();

        assertThat(result).isNotNull();
        List<TraceNode> nodes = result.nodes();
        assertThat(nodes).isNotNull().isNotEmpty();

        // Check TriplesMap outputs: only 1 statement produced for Alice (iteration #0)
        TraceNode tmNode = nodes.stream()
                .filter(n -> "TriplesMap".equals(n.nodeType()))
                .findFirst()
                .orElse(null);
        assertThat(tmNode).isNotNull();
        assertThat(tmNode.outputs()).hasSize(1);
        assertThat(tmNode.outputs().getFirst().text()).contains("Alice");

        // Verify that all trace values belong exclusively to iter-0
        for (TraceNode n : nodes) {
            for (TraceValue v : n.outputs()) {
                assertThat(v.iterationId()).isEqualTo("iter-0");
            }
        }
    }

    @Test
    public void testTraceResultBackwardProvenanceSlicing() throws ExecutionException, InterruptedException {
        // Trace backward provenance for the age triple of Alice
        TraceResultParams params = new TraceResultParams(
                new CursorSelector("src/test/resources/burp/trace_multi_pom/mapping.ttl", new Position(6, 1)),
                "http://example.org/person/Alice",
                "http://example.org/age",
                "30"
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        TraceResult result = server.traceResult(params).get();

        assertThat(result).isNotNull();
        List<TraceNode> nodes = result.nodes();
        assertThat(nodes).isNotNull().isNotEmpty();

        // 1. TriplesMap must be kept
        TraceNode tmNode = nodes.stream()
                .filter(n -> "TriplesMap".equals(n.nodeType()))
                .findFirst()
                .orElse(null);
        assertThat(tmNode).isNotNull();
        assertThat(tmNode.outputs()).hasSize(1);
        assertThat(tmNode.outputs().getFirst().text()).contains("30");

        // 2. The PredicateObjectMap for age should be kept
        boolean hasAgePom = nodes.stream()
                .filter(n -> "PredicateObjectMap".equals(n.nodeType()))
                .anyMatch(n -> n.outputs().stream().anyMatch(v -> v.text().contains("age") || v.text().contains("30")));
        assertThat(hasAgePom).as("Age POM should be included in sliced trace").isTrue();

        // 3. The Reference for $.age should be kept
        boolean hasAgeRef = nodes.stream()
                .anyMatch(n -> n.label() != null && n.label().contains("age"));
        assertThat(hasAgeRef).as("Age reference should be included in sliced trace").isTrue();

        // 4. The PredicateObjectMap for name must be PRUNED away!
        boolean hasNamePom = nodes.stream()
                .filter(n -> "PredicateObjectMap".equals(n.nodeType()))
                .anyMatch(n -> n.outputs().stream().anyMatch(v -> v.text().contains("name")));
        assertThat(hasNamePom).as("Name POM should be pruned from sliced age triple trace").isFalse();

        // 5. JSON-LD PROV must be valid
        assertThat(result.graphJsonLD())
                .isNotNull()
                .contains("TraceNode", "TraceValue");
    }

    @Test
    public void testTraceSingleTriplesMapWithReferencedParentNotInTrace() throws ExecutionException, InterruptedException {
        String mappingUri = "file:///home/jakub/Documents/DevExplore/rml-playground/configs/kgc/examples/KGC-03-io1/mapping.ttl";
        TraceIterationParams params = new TraceIterationParams(
                new IriSelector(mappingUri, "_:file:///home/jakub/Documents/DevExplore/rml-playground/configs/kgc/examples/KGC-03-io1/mapping.ttl#[8:21]"),
                2
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        TraceResult result = server.traceIteration(params).get();

        assertThat(result).isNotNull();
        List<TraceNode> nodes = result.nodes();
        assertThat(nodes).isNotNull().isNotEmpty();

        // 1. CSVSource has exactly 1 output (term is not null, no duplicate -raw)
        assertThat(nodes)
                .filteredOn(n -> n.nodeType().contains("Source") || "CSVSource".equals(n.nodeType()))
                .flatExtracting(TraceNode::outputs)
                .hasSize(1)
                .allSatisfy(v -> {
                    assertThat(v.term()).isNotNull();
                    assertThat(v.valueId()).doesNotContain("raw");
                });

        // 2. TriplesMap (map:movie) has exactly 3 statement outputs (no duplicates)
        assertThat(nodes)
                .filteredOn(n -> "TriplesMap".equals(n.nodeType()))
                .flatExtracting(TraceNode::outputs)
                .hasSize(3);

        // 3. map:person is not present in trace nodes
        assertThat(nodes)
                .extracting(TraceNode::nodeId)
                .noneMatch(id -> id.contains("person"));
    }

    @Test
    public void testTraceJoinIterationMatchesCorrectParentRow() throws ExecutionException, InterruptedException {
        String mappingUri = "file:///home/jakub/Documents/DevExplore/rml-playground/configs/kgc/examples/KGC-03-io1/mapping.ttl";
        TraceIterationParams params = new TraceIterationParams(
                new IriSelector(mappingUri, "_:file:///home/jakub/Documents/DevExplore/rml-playground/configs/kgc/examples/KGC-03-io1/mapping.ttl#[38:21]"),
                2
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        TraceResult result = server.traceIteration(params).get();

        assertThat(result).isNotNull();
        List<TraceNode> nodes = result.nodes();
        assertThat(nodes).isNotNull().isNotEmpty();

        // 1. Child JSON source has Sarah's payload
        assertThat(nodes)
                .filteredOn(n -> n.nodeType().contains("JSONSource") || "JSONSourceRFC".equals(n.nodeType()))
                .flatExtracting(TraceNode::outputs)
                .extracting(TraceValue::text)
                .anySatisfy(text -> assertThat(text).contains("Sarah"));

        // 2. Parent CSV source has 'fotr' (The Fellowship of the Ring), NOT 'wam'
        assertThat(nodes)
                .filteredOn(n -> n.nodeType().contains("CSVSource"))
                .flatExtracting(TraceNode::outputs)
                .extracting(TraceValue::text)
                .allSatisfy(text -> {
                    assertThat(text).contains("fotr");
                    assertThat(text).doesNotContain("wam");
                    assertThat(text).doesNotContain("Sarah");
                });

        // 3. ChildMap produced 'fotr' and ParentMap produced 'fotr'
        assertThat(nodes)
                .filteredOn(n -> "ChildMap".equals(n.nodeType()))
                .flatExtracting(TraceNode::outputs)
                .extracting(TraceValue::text)
                .containsExactly("\"fotr\"");

        assertThat(nodes)
                .filteredOn(n -> "ParentMap".equals(n.nodeType()))
                .flatExtracting(TraceNode::outputs)
                .extracting(TraceValue::text)
                .containsExactly("\"fotr\"");

        // 4. ReferencingObjectMap produced ex:movie/fotr (compacted using prefixMap)
        assertThat(nodes)
                .filteredOn(n -> "ReferencingObjectMap".equals(n.nodeType()))
                .flatExtracting(TraceNode::outputs)
                .extracting(TraceValue::text)
                .containsExactly("ex:movie/fotr");

        // 5. Parent TriplesMap itself is NOT present in trace nodes (only parent subject map)
        assertThat(nodes)
                .filteredOn(n -> "TriplesMap".equals(n.nodeType()))
                .extracting(TraceNode::label)
                .noneMatch(label -> label != null && label.contains("movie"));
    }
}
