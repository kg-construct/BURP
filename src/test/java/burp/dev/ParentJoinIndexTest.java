package burp.dev;

import burp.model.Iteration;
import burp.model.JoinCondition;
import burp.model.TemplateReferenceSafety;
import burp.model.context.EvaluationContext;
import burp.model.join.CanonicalJoinKey;
import burp.model.join.ParentJoinIndex;
import burp.model.lv.LogicalIteration;
import burp.model.rdf.IRITerm;
import burp.model.rdf.LiteralTerm;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ParentJoinIndexTest {

    @Test
    public void testCanonicalJoinKeySemanticEquality() {
        CanonicalJoinKey k1 = CanonicalJoinKey.of("Alice");
        CanonicalJoinKey k2 = CanonicalJoinKey.of(new LiteralTerm("Alice"));
        CanonicalJoinKey k3 = CanonicalJoinKey.of(new IRITerm("http://example.org/Alice"));

        assertThat(k1).isEqualTo(k2);
        assertThat(k1.hashCode()).isEqualTo(k2.hashCode());
        assertThat(k1).isNotEqualTo(k3);
    }

    @Test
    public void testParentJoinIndexHashLookup() {
        EvaluationContext ctx = EvaluationContext.createDefault().withTemplateSafety(TemplateReferenceSafety.UNSAFE);

        LogicalIteration p1 = new LogicalIteration(Collections.emptySet());
        p1.put("id", "100");
        p1.put("name", "Department A");

        LogicalIteration p2 = new LogicalIteration(Collections.emptySet());
        p2.put("id", "200");
        p2.put("name", "Department B");

        List<Iteration> parentIterations = List.of(p1, p2);

        // JoinCondition: child.dept_id == parent.id
        JoinCondition jc = new JoinCondition();
        burp.model.ChildMap childMap = new burp.model.ChildMap();
        childMap.expression = new burp.model.Reference("dept_id", null) {
            @Override
            public List<Object> getValues(Iteration i) {
                if (i instanceof LogicalIteration li) {
                    return Collections.singletonList(li.map.get("dept_id"));
                }
                return Collections.emptyList();
            }
        };
        burp.model.ParentMap parentMap = new burp.model.ParentMap();
        parentMap.expression = new burp.model.Reference("id", null) {
            @Override
            public List<Object> getValues(Iteration i) {
                if (i instanceof LogicalIteration li) {
                    return Collections.singletonList(li.map.get("id"));
                }
                return Collections.emptyList();
            }
        };
        jc.childMap = childMap;
        jc.parentMap = parentMap;

        ParentJoinIndex index = ParentJoinIndex.build(parentIterations, List.of(jc), ctx);

        // Child 1 matches Department A (100)
        LogicalIteration c1 = new LogicalIteration(Collections.emptySet());
        c1.put("dept_id", "100");
        List<Iteration> matches1 = index.findMatches(c1, List.of(jc), ctx);
        assertThat(matches1).hasSize(1).contains(p1);

        // Child 2 matches Department B (200)
        LogicalIteration c2 = new LogicalIteration(Collections.emptySet());
        c2.put("dept_id", "200");
        List<Iteration> matches2 = index.findMatches(c2, List.of(jc), ctx);
        assertThat(matches2).hasSize(1).contains(p2);

        // Child 3 no match (999)
        LogicalIteration c3 = new LogicalIteration(Collections.emptySet());
        c3.put("dept_id", "999");
        List<Iteration> matches3 = index.findMatches(c3, List.of(jc), ctx);
        assertThat(matches3).isEmpty();
    }
}
