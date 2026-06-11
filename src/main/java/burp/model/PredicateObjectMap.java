package burp.model;

import burp.model.rdf.IRITerm;
import burp.model.rdf.Term;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static burp.model.LogicalTarget.unionLogicalTargets;

public class PredicateObjectMap implements LogicalTargetScope, PlanNode {
    private final Set<LogicalTarget> logicalTargets = new HashSet<>();
    public List<PredicateMap> predicateMaps = new ArrayList<>();
    public List<BaseObjectMap> objectMaps = new ArrayList<>();
    public List<GraphMap> graphMaps = new ArrayList<>();

    private PlanNode parent = null;

    @Override
    public Set<LogicalTarget> getLogicalTargets() {
        return logicalTargets;
    }

    @Override
    public PlanNode getParent() {
        return parent;
    }

    @Override
    public void setParent(PlanNode parent) {
        this.parent = parent;
    }

    @Override
    public Iterable<PlanNode> children() {
        return () -> Stream.of(
                predicateMaps.stream(),
                objectMaps.stream(),
                graphMaps.stream()
        ).flatMap(s -> s).map(node -> (PlanNode) node).iterator();
    }

    @Override
    public Iterable<PlanNode> dependencies() {
        return children();
    }

    public List<RdfPredicateObject> generate(Iteration i) {
        List<RdfPredicateObject> lists = new ArrayList<>();

        for (PredicateMap pm : predicateMaps) {
            var predicates = pm.generateTerms(i);
            for (Term p : predicates) {
                if (!(p instanceof IRITerm pIri)) continue;

                for (BaseObjectMap om : objectMaps) {
                    var objects = om.generateTerms(i);
                    for (Term o : objects) {
                        if (graphMaps.isEmpty()) {
                            lists.add(new RdfPredicateObject(pIri, o, null, unionLogicalTargets(p.targets(), o.targets())));
                        } else {
                            for (GraphMap gm : graphMaps) {
                                var graphs = gm.generateTerms(i);
                                for (Term g : graphs) {
                                    if (g instanceof IRITerm gIri) {
                                        // Graph targets are NOT applied here. They are applied per-graph in TriplesMap
                                        // to avoid graph-level targets leaking across different named graphs.
                                        lists.add(new RdfPredicateObject(pIri, o, gIri, unionLogicalTargets(p.targets(), o.targets())));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return lists;
    }
}
