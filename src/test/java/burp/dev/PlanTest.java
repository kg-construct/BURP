package burp.dev;

import burp.dev.rml.RmlDevServerImpl;
import burp.dev.rml.RmlLanguageServer;
import burp.dev.rml.service.selector.CursorSelector;
import burp.dev.rml.service.selector.IdSelector;
import burp.dev.rml.service.selector.IriSelector;
import burp.dev.rml.service.plan.PlanAttributeItem;
import burp.dev.rml.service.plan.PlanNodeItem;
import burp.dev.rml.service.plan.PlanParams;
import burp.dev.rml.service.plan.PlanResult;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class PlanTest {

    @Test
    public void testPlanHierarchyAndParameters() throws ExecutionException, InterruptedException {
        String mappingUri = "src/test/resources/burp/eval_alice/mapping.ttl";
        PlanParams params = new PlanParams(mappingUri, null);

        RmlLanguageServer server = new RmlDevServerImpl();
        PlanResult result = server.plan(params).get();

        assertThat(result).isNotNull();
        assertThat(result.roots()).hasSize(1);
        assertThat(result.selectedNodeId()).isNull();

        PlanNodeItem tm = result.roots().getFirst();
        assertThat(tm.type()).isEqualTo("TriplesMap");
        assertThat(tm.id()).isEqualTo("tm1");
        assertThat(tm.resource()).isEqualTo("http://example.org/PersonMapping");
        assertThat(tm.displayResource()).isNull();
        assertThat(tm.children()).isNotEmpty();
        assertThat(tm.originRange()).isNotEmpty();

        // Find SubjectMap and ObjectMap in children
        PlanNodeItem subjectMap = tm.children().stream()
                .filter(c -> "SubjectMap".equalsIgnoreCase(c.type()))
                .findFirst()
                .orElse(null);
        assertThat(subjectMap).isNotNull();
        assertThat(subjectMap.attributes())
                .filteredOn(a -> "template".equals(a.key()))
                .extracting(PlanAttributeItem::value)
                .containsExactly("http://example.org/person/{$.name}");

        PlanNodeItem pom = tm.children().stream()
                .filter(c -> "PredicateObjectMap".equalsIgnoreCase(c.type()))
                .findFirst()
                .orElse(null);
        assertThat(pom).isNotNull();

        PlanNodeItem objectMap = pom.children().stream()
                .filter(c -> "ObjectMap".equalsIgnoreCase(c.type()))
                .findFirst()
                .orElse(null);
        assertThat(objectMap).isNotNull();
        assertThat(objectMap.attributes())
                .filteredOn(a -> "reference".equals(a.key()))
                .extracting(PlanAttributeItem::value)
                .containsExactly("$.age");
    }

    @Test
    public void testPlanWithCursorSelector() throws ExecutionException, InterruptedException {
        String mappingUri = "src/test/resources/burp/eval_alice/mapping.ttl";
        // Position at line 19 (rml:reference "$.age")
        PlanParams params = new PlanParams(
                mappingUri,
                new CursorSelector(mappingUri, new Position(19, 15))
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        PlanResult result = server.plan(params).get();

        assertThat(result).isNotNull();
        assertThat(result.roots()).hasSize(1);
        assertThat(result.selectedNodeId()).isNotNull();
    }

    @Test
    public void testPlanWithIriSelector() throws ExecutionException, InterruptedException {
        String mappingUri = "src/test/resources/burp/eval_alice/mapping.ttl";
        PlanParams params = new PlanParams(
                mappingUri,
                new IriSelector(mappingUri, "http://example.org/PersonMapping")
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        PlanResult result = server.plan(params).get();

        assertThat(result).isNotNull();
        assertThat(result.selectedNodeId()).isEqualTo("tm1");
    }

    @Test
    public void testPlanWithIriSelectorCurie() throws ExecutionException, InterruptedException {
        String mappingUri = "src/test/resources/burp/eval_alice/mapping.ttl";
        PlanParams params = new PlanParams(
                mappingUri,
                new IriSelector(mappingUri, "ex:PersonMapping")
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        PlanResult result = server.plan(params).get();

        assertThat(result).isNotNull();
        assertThat(result.selectedNodeId()).isEqualTo("tm1");
    }

    @Test
    public void testPlanWithIdSelector() throws ExecutionException, InterruptedException {
        String mappingUri = "src/test/resources/burp/eval_alice/mapping.ttl";
        PlanParams params = new PlanParams(
                mappingUri,
                new IdSelector(mappingUri, "tm1")
        );

        RmlLanguageServer server = new RmlDevServerImpl();
        PlanResult result = server.plan(params).get();

        assertThat(result).isNotNull();
        assertThat(result.selectedNodeId()).isEqualTo("tm1");
    }

    @Test
    public void testPlanWithCrossTreeReferences() throws ExecutionException, InterruptedException {
        String mappingUri = "src/test/resources/burp/trace_join_condition/mapping.ttl";
        PlanParams params = new PlanParams(mappingUri, null);

        RmlLanguageServer server = new RmlDevServerImpl();
        PlanResult result = server.plan(params).get();

        assertThat(result).isNotNull();
        assertThat(result.roots()).hasSize(2);

        PlanNodeItem childTm = result.roots().stream()
                .filter(r -> r.resource() != null && r.resource().contains("ChildMapping"))
                .findFirst()
                .orElse(null);
        assertThat(childTm).isNotNull();

        PlanNodeItem parentTm = result.roots().stream()
                .filter(r -> r.resource() != null && r.resource().contains("ParentMapping"))
                .findFirst()
                .orElse(null);
        assertThat(parentTm).isNotNull();

        // Navigate to ReferencingObjectMap inside ChildMapping -> PredicateObjectMap -> ReferencingObjectMap
        PlanNodeItem pom = childTm.children().stream()
                .filter(c -> "PredicateObjectMap".equalsIgnoreCase(c.type()))
                .findFirst()
                .orElse(null);
        assertThat(pom).isNotNull();

        PlanNodeItem rom = pom.children().stream()
                .filter(c -> "ReferencingObjectMap".equalsIgnoreCase(c.type()))
                .findFirst()
                .orElse(null);
        assertThat(rom).isNotNull();

        // ReferencingObjectMap should have a cross-tree reference to ParentMapping
        assertThat(rom.references())
                .hasSize(1)
                .first()
                .satisfies(ref -> {
                    assertThat(ref.role()).isEqualTo("parentTriplesMap");
                    assertThat(ref.targetId()).isEqualTo(parentTm.id());
                    assertThat(ref.targetResource()).isEqualTo(parentTm.resource());
                    assertThat(ref.originRange()).isNotNull();
                });

        // JoinCondition should be a child of ReferencingObjectMap and have child/parent attributes
        assertThat(rom.children())
                .filteredOn(c -> "JoinCondition".equalsIgnoreCase(c.type()))
                .hasSize(1)
                .first()
                .satisfies(jc -> {
                    assertThat(jc.attributes())
                            .extracting(PlanAttributeItem::key, PlanAttributeItem::value)
                            .contains(
                                    tuple("child", "$.deptId"),
                                    tuple("parent", "$.id")
                            );
                });
    }

    @Test
    public void testPlanWithStandaloneLogicalViewAtRoot() throws ExecutionException, InterruptedException {
        String mappingUri = "src/test/resources/burp/eval_logicalview/mapping.ttl";
        PlanParams params = new PlanParams(mappingUri, null);

        RmlLanguageServer server = new RmlDevServerImpl();
        PlanResult result = server.plan(params).get();

        dumpRoots(result.roots(), 0);
        // Roots should contain TriplesMap (:tm) and standalone LogicalView (:jsonView)
        assertThat(result.roots()).hasSize(2);

        PlanNodeItem tmRoot = result.roots().stream()
                .filter(r -> "TriplesMap".equals(r.type()))
                .findFirst()
                .orElse(null);
        assertThat(tmRoot).isNotNull();

        PlanNodeItem lvRoot = result.roots().stream()
                .filter(r -> "LogicalView".equals(r.type()))
                .findFirst()
                .orElse(null);
        assertThat(lvRoot).isNotNull();
        assertThat(lvRoot.resource()).isEqualTo("http://example.org/jsonView");

        // Verify that ViewJoin inside tmRoot has a cross-tree reference pointing to lvRoot
        PlanNodeItem csvView = tmRoot.children().stream()
                .filter(c -> "LogicalView".equals(c.type()))
                .findFirst()
                .orElse(null);
        assertThat(csvView).isNotNull();

        PlanNodeItem viewJoin = csvView.children().stream()
                .filter(c -> "ViewJoin".equals(c.type()))
                .findFirst()
                .orElse(null);
        assertThat(viewJoin).isNotNull();

        assertThat(viewJoin.references())
                .hasSize(1)
                .first()
                .satisfies(ref -> {
                    assertThat(ref.role()).isEqualTo("parentLogicalView");
                    assertThat(ref.targetId()).isEqualTo(lvRoot.id());
                    assertThat(ref.targetResource()).isEqualTo(lvRoot.resource());
                });
    }

    @Test
    public void testPlanForKgc10() throws ExecutionException, InterruptedException {
        String mappingUri = "/home/jakub/Documents/DevExplore/rml-playground/configs/kgc/examples/KGC-10-lv2/mapping.ttl";
        PlanParams params = new PlanParams(mappingUri, null);

        RmlLanguageServer server = new RmlDevServerImpl();
        PlanResult result = server.plan(params).get();

        System.err.println("=== KGC-10 FULL TREE ===");
        dumpRootsErr(result.roots(), 0);
        System.err.println("=== END KGC-10 FULL TREE ===");
    }

    private void dumpRootsErr(java.util.List<PlanNodeItem> items, int depth) {
        for (PlanNodeItem item : items) {
            System.err.println("  ".repeat(depth) + item.type() + " [" + item.id() + "] " + (item.displayResource() != null ? item.displayResource() : "") + " " + item.attributes());
            if (item.children() != null) {
                dumpRootsErr(item.children(), depth + 1);
            }
        }
    }

    private void dumpRoots(java.util.List<PlanNodeItem> items, int depth) {
        for (PlanNodeItem item : items) {
            System.out.println("  ".repeat(depth) + item.type() + " [" + item.id() + "] " + (item.displayResource() != null ? item.displayResource() : "") + " " + item.attributes());
            if (item.children() != null) {
                dumpRoots(item.children(), depth + 1);
            }
        }
    }
}
