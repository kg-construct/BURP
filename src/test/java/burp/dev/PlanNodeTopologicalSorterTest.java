package burp.dev;

import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.trace.PlanNodeStage;
import burp.dev.rml.service.trace.PlanNodeTopologicalSorter;
import burp.model.*;
import burp.model.fnml.FunctionExecution;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class PlanNodeTopologicalSorterTest {

    @Test
    public void testTopologicalSortAliceMapping() throws Exception {
        MappingWorkspace workspace = new MappingWorkspace();
        var loaded = workspace.loadMappingDetailed("src/test/resources/burp/eval_alice/mapping.ttl");
        TriplesMap tm = loaded.document().triplesMaps.getFirst();

        Set<PlanNode> allNodes = new LinkedHashSet<>();
        collectAllNodes(tm, allNodes);

        List<PlanNode> sorted = PlanNodeTopologicalSorter.sort(allNodes);

        assertThat(sorted).hasSameSizeAs(allNodes);
        assertThat(sorted).containsAll(allNodes);

        // Logical Source must be at the very front
        assertThat(sorted.getFirst()).isInstanceOf(AbstractLogicalSource.class);

        // TriplesMap must be at the very end
        assertThat(sorted.getLast()).isEqualTo(tm);

        // Verify that for each node, its dependencies appear BEFORE it
        assertDependenciesPrecede(sorted);
    }

    @Test
    public void testTopologicalSortCompleteFunctionMapping() throws Exception {
        MappingWorkspace workspace = new MappingWorkspace();
        var loaded = workspace.loadMappingDetailed("src/test/resources/burp/complete_function/mapping.ttl");
        TriplesMap tm = loaded.document().triplesMaps.getFirst();

        Set<PlanNode> allNodes = new LinkedHashSet<>();
        collectAllNodes(tm, allNodes);

        List<PlanNode> sorted = PlanNodeTopologicalSorter.sort(allNodes);

        assertThat(sorted).hasSameSizeAs(allNodes);
        assertThat(sorted).containsAll(allNodes);

        // Verify LogicalSource appears before References
        int lsIdx = indexOfFirst(sorted, AbstractLogicalSource.class);
        int refIdx = indexOfFirst(sorted, Reference.class);
        int fnIdx = indexOfFirst(sorted, FunctionExecution.class);
        int pomIdx = indexOfFirst(sorted, PredicateObjectMap.class);
        int tmIdx = sorted.indexOf(tm);

        assertThat(lsIdx).isLessThan(refIdx);
        assertThat(refIdx).isLessThan(fnIdx);
        assertThat(fnIdx).isLessThan(pomIdx);
        assertThat(pomIdx).isLessThan(tmIdx);

        // Verify that every node's declared dependencies precede it in the list
        assertDependenciesPrecede(sorted);
    }

    @Test
    public void testEmptyAndSingleNode() {
        assertThat(PlanNodeTopologicalSorter.sort(List.of())).isEmpty();

        TriplesMap tm = new TriplesMap();
        List<PlanNode> single = PlanNodeTopologicalSorter.sort(List.of(tm));
        assertThat(single).containsExactly(tm);
    }



    private void collectAllNodes(PlanNode node, Set<PlanNode> visited) {
        if (!visited.add(node)) return;
        if (node.children() != null) {
            for (PlanNode child : node.children()) {
                collectAllNodes(child, visited);
            }
        }
        if (node.dependencies() != null) {
            for (PlanNode dep : node.dependencies()) {
                collectAllNodes(dep, visited);
            }
        }
    }

    private void assertDependenciesPrecede(List<PlanNode> sorted) {
        Map<PlanNode, Integer> positions = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            positions.put(sorted.get(i), i);
        }

        for (PlanNode node : sorted) {
            int nodePos = positions.get(node);
            for (PlanNode dep : node.dependencies()) {
                if (positions.containsKey(dep)) {
                    int depPos = positions.get(dep);
                    assertThat(depPos)
                            .as("Dependency %s must precede %s", dep.getClass().getSimpleName(), node.getClass().getSimpleName())
                            .isLessThan(nodePos);
                }
            }
        }
    }

    private <T extends PlanNode> int indexOfFirst(List<PlanNode> list, Class<T> clazz) {
        for (int i = 0; i < list.size(); i++) {
            if (clazz.isInstance(list.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
