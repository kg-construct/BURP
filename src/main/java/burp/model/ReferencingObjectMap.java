package burp.model;

import burp.model.context.EvaluationContext;
import burp.model.gathermap.GatherMap;
import burp.model.join.ParentJoinIndex;
import burp.model.key.AstKey;
import burp.model.rdf.BlankNodeOrIRI;
import burp.model.rdf.Term;
import burp.parse.loader.annotations.RdfId;
import burp.parse.loader.annotations.RdfProperty;
import burp.parse.loader.annotations.RdfType;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.RER;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static burp.model.TemplateReferenceSafety.UNSAFE;

@RdfType(Rml.RefObjectMap)
public final class ReferencingObjectMap extends AbstractPlanNode implements TermGenerator, ParentJoinReferenceScope, BaseObjectMap {
    @RdfId
    public Resource subject;

    @Override
    public @Nullable Resource getIri() {
        return (subject);
    }

    @RdfProperty(Rml.parentTriplesMap)
    public TriplesMap parentTriplesMap;

    @RdfProperty(Rml.joinCondition)
    public List<JoinCondition> joinConditions = new ArrayList<>();

    @RdfProperty(Rml.gather)
    public GatherMap gatherMap;

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
    public List<PlanNodeReference> nodeReferences() {
        if (parentTriplesMap != null) {
            return List.of(new PlanNodeReference("parentTriplesMap", parentTriplesMap, originOf("parentTriplesMap")));
        }
        return Collections.emptyList();
    }

    @Override
    public List<Term> generateTerms(@Nullable Iteration i, EvaluationContext context) {
        if (gatherMap != null) {
            List<BlankNodeOrIRI> generatedIds = generateTermsFromParentJoins(i, context).stream()
                    .filter(it -> it instanceof BlankNodeOrIRI)
                    .map(it -> (BlankNodeOrIRI) it)
                    .toList();
            return gatherMap.generateTerms(i, generatedIds, context);
        }
        return generateTermsFromParentJoins(i, context);
    }

    @Override
    public List<Term> generateTerms(@Nullable Iteration i) {
        return generateTerms(i, EvaluationContext.createDefault());
    }

    @Override
    public AstKey computeAstKey() {
        String pTmIri = parentTriplesMap != null ? PlanNode.resourceToIriOrBNode(parentTriplesMap.getIri()) : "";
        AstKey parentSubjectKey = (parentTriplesMap != null && parentTriplesMap.subjectMap != null)
                ? parentTriplesMap.subjectMap.getAstKey()
                : null;
        List<AstKey> childKeys = new ArrayList<>();
        for (PlanNode child : children()) {
            childKeys.add(child.getAstKey());
        }
        return new AstKey.RefObjectMapKey(pTmIri, parentSubjectKey, childKeys);
    }

    @Override
    public List<RmlEvaluationResult> evaluateNode(@Nullable Iteration iter, @NonNull EvaluationContext context) {
        if (joinConditions.isEmpty()) {
            if (parentTriplesMap != null && parentTriplesMap.subjectMap != null) {
                return new ArrayList<>(parentTriplesMap.subjectMap.generateTerms(iter, context));
            }
            return Collections.emptyList();
        }

        if (parentTriplesMap == null || parentTriplesMap.logicalSource == null) {
            return Collections.emptyList();
        }

        var unsafeChildCtx = context.withTemplateSafety(UNSAFE);
        var unsafeParentCtx = context.withTemplateSafety(UNSAFE);

        ParentJoinIndex joinIndex = context.getJoinIndex(this);
        if (joinIndex == null) {
            Iterable<Iteration> parentIterator = context.getIterations(parentTriplesMap.logicalSource);
            joinIndex = ParentJoinIndex.build(parentIterator, joinConditions, unsafeParentCtx);
        }

        List<Iteration> matchingParents = joinIndex.findMatches(iter, joinConditions, unsafeChildCtx);
        if (!matchingParents.isEmpty() && parentTriplesMap.subjectMap != null) {
            Iteration firstMatch = matchingParents.getFirst();
            List<Term> terms = parentTriplesMap.subjectMap.generateTerms(firstMatch, context);
            if (gatherMap != null) {
                List<BlankNodeOrIRI> generatedIds = terms.stream()
                        .filter(it -> it instanceof BlankNodeOrIRI)
                        .map(it -> (BlankNodeOrIRI) it)
                        .toList();
                return new ArrayList<>(gatherMap.generateTerms(iter, generatedIds, context));
            }
            return new ArrayList<>(terms);
        }
        return Collections.emptyList();
    }

    public List<Term> generateTermsFromParentJoins(@Nullable Iteration i, EvaluationContext context) {
        if (joinConditions.isEmpty()) {
            return parentTriplesMap.subjectMap.generateTerms(i, context);
        } else {
            if (parentTriplesMap == null || parentTriplesMap.logicalSource == null) {
                throw new BurpException(new RmlError(
                        "Constant triples map in referencing object map " + this + " for triples map " + parentTriplesMap + " (without logical source) are not supported.",
                        emitOrigin("parentTriplesMap"),
                        RER.UnsupportedMapping,
                        this
                ));
            }

            var unsafeChildCtx = context.withTemplateSafety(UNSAFE);
            var unsafeParentCtx = context.withTemplateSafety(UNSAFE);

            ParentJoinIndex joinIndex = context.getJoinIndex(this);
            if (joinIndex == null) {
                Iterable<Iteration> parentIterator = context.getIterations(parentTriplesMap.logicalSource);
                joinIndex = ParentJoinIndex.build(parentIterator, joinConditions, unsafeParentCtx);
            }

            List<Iteration> matchingParents = joinIndex.findMatches(i, joinConditions, unsafeChildCtx);
            List<Term> list = new ArrayList<>();
            for (Iteration parentIteration : matchingParents) {
                list.addAll(parentTriplesMap.subjectMap.generateTerms(parentIteration, context));
            }
            return list;
        }
    }

    @Override
    public Reference buildParentJoinReference(String reference, Origin origin) {
        if (parentTriplesMap == null) {
            throw new BurpException(new RmlError(
                    "ReferencingObjectMap is missing parentTriplesMap",
                    origin != null ? origin : emitOrigin("parentTriplesMap"),
                    RER.UnsupportedMapping,
                    this
            ));
        }
        return parentTriplesMap.buildLocalReference(reference, origin);
    }

    @Override
    public @Nullable AbstractLogicalSource getParentLogicalSource() {
        return parentTriplesMap != null ? parentTriplesMap.logicalSource : null;
    }

    @Override
    public @Nullable Reference buildChildJoinReference(String reference, Origin origin) {
        LocalReferenceScope childScope = ancestor(LocalReferenceScope.class);
        return childScope != null ? childScope.buildLocalReference(reference, origin) : null;
    }

    @Override
    public @Nullable AbstractLogicalSource getChildLogicalSource() {
        LocalReferenceScope childScope = ancestor(LocalReferenceScope.class);
        return childScope != null ? childScope.getLogicalSource() : null;
    }
}
