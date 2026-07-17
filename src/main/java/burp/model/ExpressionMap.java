package burp.model;

import burp.model.fnml.FunctionExecution;
import burp.model.rdf.*;
import burp.parse.turtleprov.ProvTurtleVisitor;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.PointRange;
import burp.reporting.RmlError;
import burp.util.Util;
import burp.vocabularies.RER;
import org.apache.jena.util.URIref;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ExpressionMap implements LogicalTargetScope, PlanNode {

    private Expression expression = null;
    private Origin expressionOrigin = null;
    private final Set<LogicalTarget> logicalTargets = new HashSet<>();
    private PlanNode parent = null;

    private String baseIRI = null;

    public Expression getExpression() {
        return expression;
    }

    public void setExpression(Expression expression) {
        this.expression = expression;
    }

    public Origin getExpressionOrigin() {
        return expressionOrigin;
    }

    public void setExpressionOrigin(Origin expressionOrigin) {
        this.expressionOrigin = expressionOrigin;
    }

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
        List<PlanNode> children = new ArrayList<>();
        if (expression != null) {
            children.add(expression);
        }
        return children;
    }

    @Override
    public Iterable<PlanNode> dependencies() {
        return children();
    }

    private String getBaseIRI() {
        if (baseIRI == null) {
            BaseIRIScope scope = (BaseIRIScope) ancestor(BaseIRIScope.class);
            if (scope != null) {
                baseIRI = scope.getBaseIri();
            }
        }
        return baseIRI;
    }

    @SuppressWarnings("unchecked")
    public List<Object> generateValues(Iteration i, TemplateReferenceSafety safe) {
        switch (expression) {
            case RDFNodeConstant expr -> {
                List<Object> list = new ArrayList<>();
                if (expr.constant != null) {
                    list.add(expr.constant);
                }
                return list;
            }
            case Template template -> {
                return (List<Object>) (List<?>) template.values(i, safe);
            }
            case Reference reference -> {
                return reference.values(i);
            }
            case FunctionExecution functionExecution -> {
                return functionExecution.values(i);
            }
            case null, default -> throw new BurpException(
                    new RmlError(
                            "Error generating values, expression is not supported.",
                            expressionOrigin,
                            RER.UnsupportedMapping
                    )
            );
        }
    }

    public List<IRITerm> generateUnsafeIRIs(Iteration i) {
        Set<LogicalTarget> targets = getEffectiveTargets();
        List<Object> values = generateValues(i, TemplateReferenceSafety.UNSAFE);
        List<IRITerm> result = new ArrayList<>();

        for (Object it : values) {
            if (it == null) continue;
            if (it instanceof IRITerm) {
                result.add(new IRITerm(((IRITerm) it).uri(), targets));
            } else {
                String string;
                if (it instanceof String) {
                    string = (String) it;
                } else if (it instanceof LiteralTerm) {
                    string = ((LiteralTerm) it).value();
                } else {
                    string = it.toString();
                }

                String encoded = URIref.encode(string);
                String baseIriVal = getBaseIRI();
                String baseEncoded = URIref.encode(baseIriVal + string);

                if (Util.isValidAndAbsoluteIRI(encoded)) {
                    result.add(new IRITerm(string, targets));
                } else if (Util.isValidAndAbsoluteIRI(baseEncoded)) {
                    result.add(new IRITerm(baseIriVal + string, targets));
                } else {
                    throw new BurpException(
                            new RmlError(
                                    baseIriVal + " and " + string + " do not constitute a valid UnsafeIRI",
                                    expressionOrigin,
                                    RER.InvalidIRI
                            )
                    );
                }
            }
        }
        return result;
    }

    public List<IRITerm> generateIRIs(Iteration i) {
        Set<LogicalTarget> targets = getEffectiveTargets();
        List<Object> values = generateValues(i, TemplateReferenceSafety.SAFE_IRI);
        List<IRITerm> result = new ArrayList<>();

        for (Object it : values) {
            if (it == null) continue;
            if (it instanceof IRITerm) {
                result.add(new IRITerm(((IRITerm) it).uri(), targets));
            } else {
                String string;
                if (it instanceof String) {
                    string = (String) it;
                } else if (it instanceof LiteralTerm) {
                    string = ((LiteralTerm) it).value();
                } else {
                    string = it.toString();
                }

                String baseIriVal = getBaseIRI();

                if (Util.isValidAndAbsoluteIRI(string)) {
                    result.add(new IRITerm(string, targets));
                } else if (Util.isValidAndAbsoluteIRI(baseIriVal + string)) {
                    result.add(new IRITerm(baseIriVal + string, targets));
                } else {
                    throw new BurpException(
                            new RmlError(
                                    baseIriVal + " and " + string + " do not constitute a valid IRI",
                                    expressionOrigin,
                                    RER.InvalidIRI
                            )
                    );
                }
            }
        }
        return result;
    }

    public List<IRITerm> generateURIs(Iteration i) {
        Set<LogicalTarget> targets = getEffectiveTargets();
        List<Object> values = generateValues(i, TemplateReferenceSafety.SAFE_URI);
        List<IRITerm> result = new ArrayList<>();

        for (Object it : values) {
            if (it == null) continue;
            if (it instanceof IRITerm) {
                result.add(new IRITerm(((IRITerm) it).uri(), targets));
            } else {
                String string;
                if (it instanceof String) {
                    string = (String) it;
                } else {
                    string = it.toString();
                }

                String baseIriVal = getBaseIRI();

                if (Util.isValidAndAbsoluteURI(string)) {
                    result.add(new IRITerm(string, targets));
                } else if (Util.isValidAndAbsoluteURI(baseIriVal + string)) {
                    result.add(new IRITerm(baseIriVal + string, targets));
                } else {
                    throw new BurpException(
                            new RmlError(
                                    baseIriVal + " and " + string + " do not constitute a valid URI",
                                    expressionOrigin,
                                    RER.InvalidURI
                            )
                    );
                }
            }
        }
        return result;
    }

    protected List<BlankNodeTerm> generateBlankNodes(Iteration i) {
        Set<LogicalTarget> targets = getEffectiveTargets();

        switch (expression) {
            case RDFNodeConstant expr -> {
                if (expr.constant instanceof BlankNodeTerm constant) {
                    return Collections.singletonList(new BlankNodeTerm(constant.id(), targets));
                } else {
                    return Collections.emptyList();
                }
            }
            case Template template -> {
                return template.values(i, TemplateReferenceSafety.UNSAFE).stream()
                        .filter(Objects::nonNull)
                        .map(val -> blankNodeFor(val, targets))
                        .toList();
            }
            case Reference reference -> {
                return reference.values(i).stream()
                        .filter(Objects::nonNull)
                        .map(val -> blankNodeFor(val, targets))
                        .toList();
            }
            case FunctionExecution functionExecution -> {
                return functionExecution.values(i).stream()
                        .filter(Objects::nonNull)
                        .map(val -> blankNodeFor(val, targets))
                        .toList();
            }
            case null -> {
                return Collections.singletonList(new BlankNodeTerm("bnode-" + (blankNodeIdCounter++), targets));
            }
            default -> throw new BurpException(
                    new RmlError(
                            "Error generating blank node for expression " + expression,
                            expressionOrigin,
                            RER.UnexpectedError
                    )
            );
        }
    }

    private BlankNodeTerm blankNodeFor(Object value, Set<LogicalTarget> targets) {
        String id = blankNodeMap.computeIfAbsent(value, k -> "bnode-" + (blankNodeIdCounter++));
        return new BlankNodeTerm(id, targets);
    }

    private Set<LogicalTarget> intersectTargets(Set<LogicalTarget> t1, Set<LogicalTarget> t2) {
        if (t1.isEmpty()) return t2;
        if (t2.isEmpty()) return t1;
        Set<LogicalTarget> intersection = new HashSet<>(t1);
        intersection.retainAll(t2);
        return intersection;
    }

    protected List<LiteralTerm> generateLiterals(Iteration i, DatatypeMap dm, LanguageMap lm) {
        List<IRITerm> datatypes = dm != null ? dm.generateIRIs(i) : null;
        List<LanguageTag> languages = lm != null ? lm.generateLanguageTags(i) : null;
        Set<LogicalTarget> baseTargets = getEffectiveTargets();

        switch (expression) {
            case RDFNodeConstant expr -> {
                if (expr.constant instanceof LiteralTerm constant) {
                    return Collections.singletonList(new LiteralTerm(constant.value(), constant.datatype(), constant.language(), baseTargets));
                } else {
                    return Collections.emptyList();
                }
            }
            case Template template -> {
                return template.values(i, TemplateReferenceSafety.UNSAFE).stream()
                        .flatMap(val -> literalFor(val, datatypes, languages, baseTargets).stream())
                        .toList();
            }
            case Reference reference -> {
                return reference.values(i).stream()
                        .flatMap(val -> literalFor(val, datatypes, languages, baseTargets).stream())
                        .toList();
            }
            case FunctionExecution functionExecution -> {
                return functionExecution.values(i).stream()
                        .flatMap(val -> literalFor(val, datatypes, languages, baseTargets).stream())
                        .toList();
            }
            case null, default -> throw new BurpException(
                    new RmlError(
                            "Error generating literal for expression " + expression,
                            expressionOrigin,
                            RER.UnexpectedError
                    )
            );
        }
    }

    private List<LiteralTerm> literalFor(Object value, List<IRITerm> datatypes, List<LanguageTag> languages, Set<LogicalTarget> baseTargets) {
        if (value == null) {
            return Collections.emptyList();
        }

        if (languages != null) {
            return languages.stream()
                    .map(langTag -> new LiteralTerm(value.toString(), null, langTag.tag(), intersectTargets(baseTargets, langTag.targets())))
                    .toList();
        }
        if (datatypes != null) {
            return datatypes.stream()
                    .map(dt -> {
                        Term term = Datardf.toTerm(value, dt);
                        if (term instanceof LiteralTerm lt) {
                            return new LiteralTerm(lt.value(), lt.datatype(), lt.language(), intersectTargets(baseTargets, dt.targets()));
                        }
                        return new LiteralTerm(value.toString(), dt, null, intersectTargets(baseTargets, dt.targets()));
                    })
                    .toList();
        }

        Term term = Datardf.toTerm(value);
        if (term instanceof LiteralTerm lt) {
            return List.of(new LiteralTerm(lt.value(), lt.datatype(), lt.language(), baseTargets));
        } else {
            return Collections.singletonList(new LiteralTerm(value.toString(), null, null, baseTargets));
        }

    }

    @Override
    public List<PointRange> nodeRanges() {
        if (expressionOrigin == null || expressionOrigin.sourceStatements() == null) {
            return Collections.emptyList();
        }
        return ProvTurtleVisitor.retrieveTurtleLocation(expressionOrigin.sourceStatements());
    }

    private static long blankNodeIdCounter = 0L;
    private static final Map<Object, String> blankNodeMap = new ConcurrentHashMap<>();
}