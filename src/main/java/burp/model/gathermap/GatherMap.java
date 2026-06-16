package burp.model.gathermap;

import burp.model.Iteration;
import burp.model.PlanNode;
import burp.model.TermGenerator;
import burp.model.rdf.*;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GatherMap implements PlanNode {

    private PlanNode parent = null;
    public boolean allowEmptyListAndContainer = false;
    public Resource gatherAs = null;
    public Resource strategy = RML.append;
    public Origin strategyOrigin = null;
    public List<TermGenerator> gatherMaps = new ArrayList<>();

    private static long idCounter = 0L;

    public static BlankNodeTerm nextId() {
        return new BlankNodeTerm("gathermap-" + (idCounter++));
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
    public List<PlanNode> children() {
        return new ArrayList<>(gatherMaps);
    }

    @Override
    public List<PlanNode> dependencies() {
        return children();
    }

    public List<Term> generateTerms(Iteration i, List<BlankNodeOrIRI> explicitIds) {
        var superCollection = gatherMaps.stream()
                .map(gm -> gm.generateTerms(i))
                .toList();

        List<? extends List<? extends Term>> itemSets;

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
                    strategyOrigin,
                    RER.OutOfSpec,
                    null,
                    Map.of(RML.strategy, strategy)
                )
            );
        }

        List<Term> results = new ArrayList<>();

        for (List<? extends Term> items : itemSets) {
            List<BlankNodeOrIRI> idsToUse;
            boolean idGenerated;
            if (explicitIds != null) {
                idsToUse = explicitIds;
                idGenerated = false;
            } else {
                idsToUse = List.of(nextId());
                idGenerated = true;
            }

            for (BlankNodeOrIRI id : idsToUse) {
                if (items.isEmpty() && !allowEmptyListAndContainer) continue;

                List<Term> itemsMutable = new ArrayList<>(items);
                CollectionOrContainerTerm container;

                if (RDF.Alt.equals(gatherAs)) {
                    container = new RdfAltTerm(itemsMutable, id, idGenerated);
                } else if (RDF.Bag.equals(gatherAs)) {
                    container = new RdfBagTerm(itemsMutable, id, idGenerated);
                } else if (RDF.Seq.equals(gatherAs)) {
                    container = new RdfSeqTerm(itemsMutable, id, idGenerated);
                } else {
                    container = new RdfListTerm(itemsMutable, id, idGenerated);
                }
                results.add(container);
            }
        }

        return results;
    }

    public List<Term> generateTerms(Iteration i) {
        return generateTerms(i, null);
    }

    private <T> List<List<T>> cartesianProduct(List<? extends List<? extends T>> lists) {
        List<List<T>> result = new ArrayList<>();
        result.add(new ArrayList<>());

        for (List<? extends T> list : lists) {
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
}
