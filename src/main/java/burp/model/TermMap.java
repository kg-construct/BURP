package burp.model;

import burp.model.gathermap.GatherMap;
import burp.model.rdf.BlankNodeOrIRI;
import burp.model.rdf.Term;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.BURP;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import org.apache.jena.rdf.model.Resource;

import java.util.*;

public abstract class TermMap extends ExpressionMap implements TermGenerator {
    public DatatypeMap datatypeMap = null;
    public LanguageMap languageMap = null;
    public Resource termType = null;
    public GatherMap gatherMap = null;

    @Override
    public Iterable<PlanNode> children() {
        List<PlanNode> children = new ArrayList<>();
        super.children().forEach(children::add);
        if (datatypeMap != null) children.add(datatypeMap);
        if (languageMap != null) children.add(languageMap);
        if (gatherMap != null) children.add(gatherMap);
        return children;
    }

    public abstract String getName();

    public abstract Set<Resource> getAllowedTermTypes();

    @Override
    public List<Term> generateTerms(Iteration i) {
        Set<Resource> allowed = this.getAllowedTermTypes();

        if (gatherMap != null && allowed.contains(BURP.CollectionOrContainer)) {
            if (getExpression() == null) {
                return gatherMap.generateTerms(i, null);
            } else {
                var generatedIds = generateExpressionTerms(i, Set.of(RML.LITERAL)).stream()
                        .filter(it -> it instanceof BlankNodeOrIRI)
                        .map(it -> (BlankNodeOrIRI) it)
                        .toList();
                return gatherMap.generateTerms(i, generatedIds);
            }
        }

        return generateExpressionTerms(i);
    }

    public List<Term> generateExpressionTerms(Iteration i) {
        return generateExpressionTerms(i, Collections.emptySet());
    }

    @SuppressWarnings("unchecked")
    public List<Term> generateExpressionTerms(Iteration i, Set<Resource> disallowed) {
        Set<Resource> allowed = new HashSet<>(this.getAllowedTermTypes());
        allowed.removeAll(disallowed);

        if (RML.IRI.equals(termType) && allowed.contains(RML.IRI)) {
            return (List<Term>) (List<?>) generateIRIs(i);
        } else if (RML.URI.equals(termType) && allowed.contains(RML.URI)) {
            return (List<Term>) (List<?>) generateURIs(i);
        } else if (RML.UnsafeIRI.equals(termType) && allowed.contains(RML.IRI)) {
            return (List<Term>) (List<?>) generateUnsafeIRIs(i);
        } else if (RML.BLANKNODE.equals(termType) && allowed.contains(RML.BLANKNODE)) {
            return (List<Term>) (List<?>) generateBlankNodes(i);
        } else if (RML.LITERAL.equals(termType) && allowed.contains(RML.LITERAL)) {
            return (List<Term>) (List<?>) generateLiterals(i, datatypeMap, languageMap);
        } else {
            throw new BurpException(
                    new RmlError(
                            "The term map " + this.getName() + " generates " + termType + ", which is not allowed. " +
                                    "Expected one of " + allowed,
                            new Origin(this, null),
                            RER.IncorrectTermType,
                            null,
                            Map.of(
                                    RML.termType, termType,
                                    BURP.allowedTermType, String.join("", allowed.stream().map(Resource::getURI).toList())
                            )
                    )
            );
        }
    }
}