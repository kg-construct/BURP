package burp.model;

import burp.model.context.EvaluationContext;
import burp.model.rdf.IRITerm;
import burp.model.rdf.Term;
import burp.parse.loader.annotations.RdfId;
import burp.parse.loader.annotations.RdfProperty;
import burp.parse.loader.annotations.RdfShortcutProperty;
import burp.parse.loader.annotations.RdfType;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static burp.model.LogicalTarget.unionLogicalTargets;

@RdfType(Rml.PredicateObjectMap)
public class PredicateObjectMap extends AbstractPlanNode implements LogicalTargetScope {
    @RdfId
    public Resource subject;

    @Override
    public @Nullable Resource getIri() {
        return (subject);
    }

    @RdfProperty(Rml.logicalTarget)
    public final Set<LogicalTarget> logicalTargets = new HashSet<>();

    @RdfProperty(Rml.predicateMap)
    @RdfShortcutProperty(shortcutUri = Rml.predicate, targetProperty = Rml.constant)
    public List<PredicateMap> predicateMaps = new ArrayList<>();

    @RdfProperty(Rml.objectMap)
    @RdfShortcutProperty(shortcutUri = Rml.object, targetProperty = Rml.constant)
    public List<BaseObjectMap> objectMaps = new ArrayList<>();

    @RdfProperty(Rml.graphMap)
    @RdfShortcutProperty(shortcutUri = Rml.graph, targetProperty = Rml.constant)
    public List<GraphMap> graphMaps = new ArrayList<>();

    public boolean isComplete() {
        return !predicateMaps.isEmpty() && !objectMaps.isEmpty();
    }

    @Override
    public Set<LogicalTarget> getLogicalTargets() {
        return logicalTargets;
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

    public List<RdfPredicateObject> generate(@Nullable Iteration i, EvaluationContext context) {
        List<RdfPredicateObject> lists = new ArrayList<>();

        for (PredicateMap pm : predicateMaps) {
            var predicates = pm.generateTerms(i, context);
            for (Term p : predicates) {
                if (!(p instanceof IRITerm pIri)) continue;

                for (BaseObjectMap om : objectMaps) {
                    var objects = om.generateTerms(i, context);
                    for (Term o : objects) {
                        if (graphMaps.isEmpty()) {
                            lists.add(new RdfPredicateObject(pIri, o, null, unionLogicalTargets(p.targets(), o.targets())));
                        } else {
                            for (GraphMap gm : graphMaps) {
                                var graphs = gm.generateTerms(i, context);
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

    public List<RdfPredicateObject> generate(@Nullable Iteration i) {
        return generate(i, EvaluationContext.createDefault());
    }

    @Override
    public List<RmlEvaluationResult> evaluateNode(@Nullable Iteration iter, EvaluationContext context) {
        // TODO: It could be nice for POM to show full RdfStatements (combining subject from TriplesMap + predicate + object),
        // but that is to be evaluated, maybe with feature flags when introduced in the extension:
        // PostHogFeatureFlagEvaluations flags = posthog.evaluateFlags("distinct_id_of_your_user");
        // if (flags.isEnabled("pom-evaluatenode-rdfstatement")) { ... }
        if (Boolean.FALSE) {
            List<RmlEvaluationResult> resList = new ArrayList<>();
            TriplesMap parentTm = ancestor(TriplesMap.class);
            if (parentTm != null) {
                List<Term> predicates = predicateMaps.stream().flatMap(pm -> pm.generateTerms(iter, context).stream()).toList();
                List<Term> objects = objectMaps.stream().flatMap(om -> om.generateTerms(iter, context).stream()).toList();
                List<Term> subjects = parentTm.subjectMap != null ? parentTm.subjectMap.generateTerms(iter, context) : java.util.Collections.emptyList();
                for (Term s : subjects) {
                    if (s instanceof burp.model.rdf.BlankNodeOrIRI sIri) {
                        for (Term p : predicates) {
                            if (p instanceof IRITerm pIri) {
                                for (Term o : objects) {
                                    OriginLineage sLineage = s.lineage() != null ? s.lineage() : (iter != null ? iter.getLineage() : null);
                                    OriginLineage finalLineage = sLineage != null ? sLineage.merge(p.lineage()).merge(o.lineage()) : (o.lineage() != null ? o.lineage() : p.lineage());
                                    resList.add(new RdfStatement(sIri, pIri, o, null, unionLogicalTargets(s.targets(), p.targets(), o.targets()), finalLineage));
                                }
                            }
                        }
                    }
                }
            }
            return resList;
        }
        return new ArrayList<>(generate(iter, context));
    }

    @Override
    public burp.model.key.AstKey computeAstKey() {
        List<burp.model.key.AstKey> childKeys = new ArrayList<>();
        for (PlanNode child : children()) {
            childKeys.add(child.getAstKey());
        }
        return new burp.model.key.AstKey.PomKey(childKeys);
    }
}
