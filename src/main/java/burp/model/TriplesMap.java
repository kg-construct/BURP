package burp.model;

import burp.Main;
import burp.model.rdf.BlankNodeOrIRI;
import burp.model.rdf.CollectionOrContainerTerm;
import burp.model.rdf.IRITerm;
import burp.model.rdf.Term;
import burp.reporting.Origin;
import org.apache.jena.rdf.model.Resource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static burp.model.LogicalTarget.unionLogicalTargets;

public class TriplesMap implements PlanNode, BaseIRIScope, LocalReferenceScope, LogicalTargetScope {
    public Resource subject;
    public AbstractLogicalSource logicalSource;
    public SubjectMap subjectMap;
    public List<PredicateObjectMap> predicateObjectMaps = new ArrayList<>();
    public String baseIRI;

    private PlanNode parent = null;
    public long countGeneratedStatements = 0;
    public final Set<LogicalTarget> logicalTargets = new HashSet<>();

    public TriplesMap(Resource subject) {
        this.subject = subject;
    }

    @Override
    public String getBaseIri() {
        return baseIRI != null ? baseIRI : Main.baseIRI;
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
        List<PlanNode> list = new ArrayList<>();
        if (logicalSource != null) list.add(logicalSource);
        if (subjectMap != null) list.add(subjectMap);
        list.addAll(predicateObjectMaps);
        return list;
    }

    @Override
    public Iterable<PlanNode> dependencies() {
        return children();
    }

    @Override
    public Set<LogicalTarget> getLogicalTargets() {
        return logicalTargets;
    }

    public List<RdfStatementLike> generate(Iteration i) {
        String tmBaseIRI = getBaseIri();
        List<RdfStatementLike> stmts = new ArrayList<>();

        Set<IRITerm> subjectGraphs = new HashSet<>();
        for (TermGenerator map : subjectMap.graphMaps) {
            for (Term t : map.generateTerms(i)) {
                if (t instanceof IRITerm) {
                    subjectGraphs.add((IRITerm) t);
                }
            }
        }

        Set<IRITerm> targetGraphsForSubjectMap = subjectMap.graphMaps.isEmpty() ? new HashSet<>() : subjectGraphs;
        if (subjectMap.graphMaps.isEmpty()) {
            targetGraphsForSubjectMap.add(null);
        }

        var subjects = subjectMap.generateTerms(i);

        for (Term s : subjects) {
            if (!(s instanceof BlankNodeOrIRI sIri)) continue;

            if (s instanceof CollectionOrContainerTerm) {
                for (IRITerm g : targetGraphsForSubjectMap) {
                    stmts.add(new RdfStatementSubjectGraph(
                            sIri, g, unionLogicalTargets(s.targets(), g != null ? g.targets() : null)
                    ));
                }
            }

            for (Resource c : subjectMap.classes) {
                Set<LogicalTarget> classTargets = subjectMap.getEffectiveTargets();
                for (IRITerm g : targetGraphsForSubjectMap) {
                    stmts.add(new RdfStatement(
                            sIri,
                            new IRITerm("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                            new IRITerm(c.getURI(), classTargets),
                            g,
                            unionLogicalTargets(s.targets(), classTargets, g != null ? g.targets() : null)
                    ));
                    countGeneratedStatements++;
                }
            }
        }

        for (PredicateObjectMap pom : predicateObjectMaps) {
            List<RdfPredicateObject> generatedPoms = pom.generate(i);
            for (Term s : subjects) {
                if (!(s instanceof BlankNodeOrIRI sIri)) continue;

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
                        stmts.add(new RdfStatement(sIri, po.predicate(), po.object(), g, finalTargets));
                        countGeneratedStatements++;
                    }
                }
            }
        }
        return stmts;
    }

    @Override
    public Reference buildLocalReference(String reference, Origin origin) {
        return logicalSource.buildExportedReference(reference, origin);
    }
}