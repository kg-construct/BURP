package burp.model.gathermap;

import burp.model.*;
import burp.model.context.EvaluationContext;
import burp.model.key.AstKey;
import burp.model.rdf.*;
import burp.parse.loader.annotations.RdfId;
import burp.parse.loader.annotations.RdfList;
import burp.parse.loader.annotations.RdfProperty;
import burp.parse.loader.annotations.RdfType;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;

@RdfType(Rml.GatherMap)
public class GatherMap extends AbstractPlanNode implements LogicalTargetScope {

    @RdfId
    public Resource subject;

    @Override
    public @NonNull Resource getIri() {
        return (subject);
    }

    @Override
    public void collectAttributes(AttributeCollector sink) {
        if (gatherAs != null) {
            Origin orig = originOf("gatherAs");
            sink.put("gatherAs", gatherAs.getLocalName(), orig);
        }
        if (strategy != null) {
            Origin orig = originOf("strategy");
            sink.put("strategy", strategy.getLocalName(), orig);
        }
    }

    @RdfProperty(Rml.logicalTarget)
    public Set<LogicalTarget> logicalTargets = new HashSet<>();

    @Override
    public Set<LogicalTarget> getLogicalTargets() {
        return logicalTargets;
    }

    @RdfProperty(Rml.allowEmptyListAndContainer)
    public boolean allowEmptyListAndContainer = false;

    @RdfProperty(Rml.gatherAs)
    public @Nullable Resource gatherAs = null;

    @RdfProperty(Rml.strategy)
    public Resource strategy = RML.append;

    @RdfList(Rml.gather)
    public List<TermGenerator> gatherMaps = new ArrayList<>();

    public static BlankNodeTerm nextId(EvaluationContext context, Set<LogicalTarget> targets, @Nullable OriginLineage lineage) {
        return new BlankNodeTerm(context.blankNodeScope().allocateFresh("gathermap-"), targets, lineage);
    }

    public static BlankNodeTerm nextId(EvaluationContext context) {
        return nextId(context, Set.of(), null);
    }


    @Override
    public List<PlanNode> children() {
        return new ArrayList<>(gatherMaps);
    }

    @Override
    public List<PlanNode> dependencies() {
        return children();
    }

    public List<Term> generateTerms(@Nullable Iteration i, @Nullable List<BlankNodeOrIRI> explicitIds, EvaluationContext context) {
        var superCollection = gatherMaps.stream()
                .map(gm -> gm.generateTerms(i, context))
                .toList();

        List<List<Term>> itemSets;

        if (RML.append.equals(strategy)) {
            var flattened = superCollection.stream()
                    .flatMap(List::stream)
                    .toList();
            itemSets = List.of(flattened);
        } else if (RML.cartesianProduct.equals(strategy)) {
            itemSets = cartesianProduct(superCollection);
        } else {
            throw new BurpException(
                new RmlError(
                    "Unknown strategy: " + strategy + ", choose either " + RML.append + " or " + RML.cartesianProduct + ".",
                    emitOrigin("strategy"),
                    RER.OutOfSpec,
                    null,
                    Map.of(RML.strategy, strategy),
                    this
                )
            );
        }

        List<Term> results = new ArrayList<>();
        OriginLineage containerLineage = (i != null) ? i.getLineage() : null;
        Set<LogicalTarget> targets = getEffectiveTargets();

        for (List<Term> items : itemSets) {
            List<BlankNodeOrIRI> idsToUse;
            boolean idGenerated;
            if (explicitIds != null) {
                idsToUse = explicitIds;
                idGenerated = false;
            } else {
                idsToUse = List.of(nextId(context, targets, containerLineage));
                idGenerated = true;
            }

            for (BlankNodeOrIRI id : idsToUse) {
                if (items.isEmpty() && !allowEmptyListAndContainer) continue;

                Set<LogicalTarget> effectiveTargets = !id.targets().isEmpty() ? id.targets() : targets;
                OriginLineage effectiveLineage = id.lineage() != null
                        ? (containerLineage != null ? containerLineage.merge(id.lineage()) : id.lineage())
                        : containerLineage;

                CollectionOrContainerTerm container = buildCollectionOrContainer(items, id, idGenerated, effectiveTargets, effectiveLineage);
                results.add(container);
            }
        }

        return results;
    }

    private @NonNull CollectionOrContainerTerm buildCollectionOrContainer(
            List<Term> items,
            BlankNodeOrIRI id,
            boolean idGenerated,
            Set<LogicalTarget> targets,
            @Nullable OriginLineage lineage
    ) {
        List<Term> itemsMutable = new ArrayList<>(items);
        if (RDF.Alt.equals(gatherAs)) {
            return new RdfAltTerm(itemsMutable, id, idGenerated, targets, lineage);
        } else if (RDF.Bag.equals(gatherAs)) {
            return new RdfBagTerm(itemsMutable, id, idGenerated, targets, lineage);
        } else if (RDF.Seq.equals(gatherAs)) {
            return new RdfSeqTerm(itemsMutable, id, idGenerated, targets, lineage);
        } else {
            return new RdfListTerm(itemsMutable, id, idGenerated, targets, lineage);
        }
    }

    public List<Term> generateTerms(@Nullable Iteration i, @Nullable List<BlankNodeOrIRI> explicitIds) {
        return generateTerms(i, explicitIds, EvaluationContext.createDefault());
    }

    public List<Term> generateTerms(@Nullable Iteration i, EvaluationContext context) {
        return generateTerms(i, null, context);
    }

    public List<Term> generateTerms(@Nullable Iteration i) {
        return generateTerms(i, null, EvaluationContext.createDefault());
    }

    private <T> List<List<T>> cartesianProduct(List<List<T>> lists) {
        List<List<T>> result = new ArrayList<>();
        result.add(new ArrayList<>());

        for (List<T> list : lists) {
            List<List<T>> currentResult = new ArrayList<>();
            for (T item : list) {
                for (List<T> combination : result) {
                    List<T> newCombination = new ArrayList<>(combination);
                    newCombination.add(item);
                    currentResult.add(newCombination);
                }
            }
            result = currentResult;
        }
        return result;
    }

    @Override
    public List<RmlEvaluationResult> evaluateNode(@Nullable Iteration iter, @NonNull EvaluationContext context) {
        return new ArrayList<>(generateTerms(iter, context));
    }

    @Override
    public AstKey computeAstKey() {
        String strat = strategy != null ? strategy.getURI() : "append";
        List<AstKey> childKeys = new ArrayList<>();
        for (PlanNode child : children()) {
            childKeys.add(child.getAstKey());
        }
        return new AstKey.GatherMapKey(strat, childKeys);
    }
}
