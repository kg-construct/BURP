package burp.model;

import burp.model.gathermap.GatherMap;
import burp.model.rdf.BlankNodeOrIRI;
import burp.model.rdf.Datardf;
import burp.model.rdf.Term;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.RER;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static burp.model.TemplateReferenceSafety.UNSAFE;

public final class ReferencingObjectMap implements TermGenerator, PlanNode, ParentJoinReferenceScope, BaseObjectMap {
    public TriplesMap parentTriplesMap;
    public List<JoinCondition> joinConditions = new ArrayList<>();

    public GatherMap gatherMap;
    private PlanNode parent = null;
    public final Set<LogicalTarget> logicalTargets = new HashSet<>();

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
        List<PlanNode> list = new ArrayList<>(joinConditions);
        if (gatherMap != null) {
            list.add(gatherMap);
        }
        return list;
    }

    @Override
    public Iterable<PlanNode> dependencies() {
        List<PlanNode> list = new ArrayList<>();
        children().forEach(list::add);
        if (parentTriplesMap != null && parentTriplesMap.subjectMap != null) {
            list.add(parentTriplesMap.subjectMap);
        }
        return list;
    }

    @Override
    public List<Term> generateTerms(Iteration i) {
        if (gatherMap != null) {
            List<BlankNodeOrIRI> generatedIds = generateTermsFromParentJoins(i).stream()
                    .filter(it -> it instanceof BlankNodeOrIRI)
                    .map(it -> (BlankNodeOrIRI) it)
                    .toList();
            return gatherMap.generateTerms(i, generatedIds);
        }
        return generateTermsFromParentJoins(i);
    }

    public List<Term> generateTermsFromParentJoins(Iteration i) {
        if (joinConditions.isEmpty()) {
            return parentTriplesMap.subjectMap.generateTerms(i);
        } else {
            List<Term> list = new ArrayList<>();
            var parentIterator = parentTriplesMap.logicalSource.iterator();
            if (parentIterator == null) {
                throw new BurpException(new RmlError(
                        "Constant triples map in referencing object map " + this + " for triples map " + parentTriplesMap + " (without logical source) are not supported.",
                        null,
                        RER.UnsupportedMapping
                ));
            }

            for (Iteration parentIteration : parentIterator) {
                boolean ok = true;
                for (JoinCondition jc : joinConditions) {
                    List<Object> valuesChild = jc.childMap.generateValues(i, UNSAFE);
                    List<Object> valuesParent = jc.parentMap.generateValues(parentIteration, UNSAFE);

                    boolean matchFound = valuesChild.stream().anyMatch(vC -> valuesParent.stream().anyMatch(vP -> Datardf.semanticEquals(vC, vP)));
                    if (!matchFound) {
                        ok = false;
                        break;
                    }
                }

                if (ok) {
                    list.addAll(parentTriplesMap.subjectMap.generateTerms(parentIteration));
                }
            }
            return list;
        }
    }

    @Override
    public Reference buildParentJoinReference(String reference, Origin origin) {
        if (parentTriplesMap == null) {
            throw new BurpException(new RmlError(
                    "ReferencingObjectMap is missing parentTriplesMap",
                    origin,
                    RER.UnsupportedMapping
            ));
        }
        return parentTriplesMap.buildLocalReference(reference, origin);
    }
}