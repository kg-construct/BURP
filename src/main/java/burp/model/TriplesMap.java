package burp.model;

import burp.model.context.EvaluationContext;
import burp.model.rdf.BlankNodeOrIRI;
import burp.model.rdf.CollectionOrContainerTerm;
import burp.model.rdf.IRITerm;
import burp.model.rdf.Term;
import burp.parse.loader.annotations.RdfId;
import burp.parse.loader.annotations.RdfProperty;
import burp.parse.loader.annotations.RdfShortcutProperty;
import burp.parse.loader.annotations.RdfType;
import burp.reporting.Origin;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Resource;
import burp.model.key.AstKey;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static burp.model.LogicalTarget.unionLogicalTargets;

@RdfType(Rml.TriplesMap)
public class TriplesMap extends AbstractPlanNode implements BaseIRIScope, LocalReferenceScope, LogicalTargetScope {
    @RdfId
    public Resource subject;

    @RdfProperty(Rml.logicalSource)
    public AbstractLogicalSource logicalSource;

    @RdfProperty(Rml.subjectMap)
    @RdfShortcutProperty(shortcutUri = Rml.subject, targetProperty = Rml.constant)
    public SubjectMap subjectMap;

    @RdfProperty(Rml.predicateObjectMap)
    public List<PredicateObjectMap> predicateObjectMaps = new ArrayList<>();

    @RdfProperty(Rml.baseIRI)
    public String baseIRI;

    @RdfProperty(Rml.logicalTarget)
    public final Set<LogicalTarget> logicalTargets = new HashSet<>();


    public long countGeneratedStatements = 0;

    public TriplesMap() {}

    @Override
    public String getBaseIri() {
        return baseIRI;
    }

    @Override
    public void collectAttributes(AttributeCollector sink) {
        if (baseIRI != null) {
            Origin orig = originOf("baseIRI");
            sink.put("baseIRI", baseIRI, orig);
        }
    }

    @Override
    public Iterable<PlanNode> children() {
        List<PlanNode> list = new ArrayList<>();
        if (logicalSource != null) list.add(logicalSource);
        if (subjectMap != null) list.add(subjectMap);
        list.addAll(predicateObjectMaps);
        return list;
    }

    @Override
    public Iterable<PlanNode> dependencies() {
        List<PlanNode> list = new ArrayList<>();
        if (subjectMap != null) list.add(subjectMap);
        list.addAll(predicateObjectMaps);
        return list;
    }

    @Override
    public Set<LogicalTarget> getLogicalTargets() {
        return logicalTargets;
    }

    public List<RdfStatementLike> generate(@Nullable Iteration i, EvaluationContext context) {
        String tmBaseIRI = (baseIRI != null) ? baseIRI : (context.baseIri() != null ? context.baseIri() : getBaseIri());
        EvaluationContext tmCtx = (tmBaseIRI != null && !tmBaseIRI.equals(context.baseIri()))
                ? context.withBaseIri(tmBaseIRI)
                : context;

        List<RdfStatementLike> stmts = new ArrayList<>();

        Set<IRITerm> subjectGraphs = new HashSet<>();
        if (subjectMap != null) {
            for (TermGenerator map : subjectMap.graphMaps) {
                for (Term t : map.generateTerms(i, tmCtx)) {
                    if (t instanceof IRITerm) {
                        subjectGraphs.add((IRITerm) t);
                    }
                }
            }
        }

        Set<IRITerm> targetGraphsForSubjectMap = (subjectMap != null && !subjectMap.graphMaps.isEmpty()) ? subjectGraphs : new HashSet<>();
        if (subjectMap == null || subjectMap.graphMaps.isEmpty()) {
            targetGraphsForSubjectMap.add(null);
        }

        var subjects = subjectMap != null ? subjectMap.generateTerms(i, tmCtx) : List.<Term>of();

        for (Term s : subjects) {
            if (!(s instanceof BlankNodeOrIRI sIri)) continue;

            OriginLineage primaryLineage = s.lineage() != null ? s.lineage() : (i != null ? i.getLineage() : null);

            if (s instanceof CollectionOrContainerTerm) {
                for (IRITerm g : targetGraphsForSubjectMap) {
                    OriginLineage collLineage = primaryLineage;
                    if (collLineage != null && g != null && g.lineage() != null) {
                        collLineage = collLineage.merge(g.lineage());
                    }
                    stmts.add(new RdfStatementSubjectGraph(
                            sIri, g, unionLogicalTargets(s.targets(), g != null ? g.targets() : null), collLineage
                    ));
                }
            }

            for (Resource c : subjectMap.classes) {
                Set<LogicalTarget> classTargets = subjectMap.getEffectiveTargets();
                for (IRITerm g : targetGraphsForSubjectMap) {
                    OriginLineage classLineage = primaryLineage;
                    if (classLineage != null && g != null && g.lineage() != null) {
                        classLineage = classLineage.merge(g.lineage());
                    }
                    stmts.add(new RdfStatement(
                            sIri,
                            new IRITerm("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                            new IRITerm(c.getURI(), classTargets),
                            g,
                            unionLogicalTargets(s.targets(), classTargets, g != null ? g.targets() : null),
                            classLineage
                    ));
                    countGeneratedStatements++;
                }
            }
        }

        for (PredicateObjectMap pom : predicateObjectMaps) {
            List<RdfPredicateObject> generatedPoms = pom.generate(i, tmCtx);
            for (Term s : subjects) {
                if (!(s instanceof BlankNodeOrIRI sIri)) continue;

                OriginLineage primaryLineage = s.lineage() != null ? s.lineage() : (i != null ? i.getLineage() : null);

                for (RdfPredicateObject po : generatedPoms) {
                    Set<IRITerm> combinedGraphs = new HashSet<>();
                    if (subjectGraphs.isEmpty() && pom.graphMaps.isEmpty()) {
                        combinedGraphs.add(null);
                    } else {
                        combinedGraphs.addAll(subjectGraphs);
                        if (po.graph() != null) {
                            combinedGraphs.add((IRITerm) po.graph());
                        }
                    }

                    for (IRITerm g : combinedGraphs) {
                        Set<LogicalTarget> finalTargets = unionLogicalTargets(s.targets(), po.targets(), g != null ? g.targets() : null);
                        OriginLineage finalLineage = primaryLineage;
                        if (finalLineage != null) {
                            if (po.predicate() != null && po.predicate().lineage() != null) {
                                finalLineage = finalLineage.merge(po.predicate().lineage());
                            }
                            if (po.object() != null && po.object().lineage() != null) {
                                finalLineage = finalLineage.merge(po.object().lineage());
                            }
                            if (g != null && g.lineage() != null) {
                                finalLineage = finalLineage.merge(g.lineage());
                            }
                        } else {
                            finalLineage = po.object() != null ? po.object().lineage() : null;
                        }

                        stmts.add(new RdfStatement(sIri, po.predicate(), po.object(), g, finalTargets, finalLineage));
                        countGeneratedStatements++;
                    }
                }
            }
        }
        return stmts;
    }

    public List<RdfStatementLike> generate(@Nullable Iteration i) {
        return generate(i, EvaluationContext.createDefault());
    }

    @Override
    public Reference buildLocalReference(String reference, Origin origin) {
        if (logicalSource == null) {
            return null;
        }
        return logicalSource.buildExportedReference(reference, origin);
    }

    @Override
    public @Nullable AbstractLogicalSource getLogicalSource() {
        return this.logicalSource;
    }

    @Override
    public @NonNull Resource getIri() {
        return (subject);
    }

    @Override
    public AstKey computeAstKey() {
        List<AstKey> childKeys = new ArrayList<>();
        for (PlanNode child : children()) {
            childKeys.add(child.getAstKey());
        }
        return new AstKey.TriplesMapKey(childKeys);
    }

    @Override
    public List<RmlEvaluationResult> evaluateNode(@Nullable Iteration iter, @NonNull EvaluationContext context) {
        List<RmlEvaluationResult> res = new ArrayList<>();
        for (RdfStatementLike s : generate(iter, context)) {
            if (s instanceof RmlEvaluationResult r) {
                res.add(r);
            }
        }
        return res;
    }
}
