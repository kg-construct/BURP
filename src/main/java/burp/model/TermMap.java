package burp.model;

import burp.model.context.EvaluationContext;
import burp.model.fnml.FunctionExecution;
import burp.model.gathermap.GatherMap;
import burp.model.rdf.*;
import burp.parse.ValidationMode;
import burp.parse.loader.annotations.RdfProperty;
import burp.parse.loader.annotations.RdfShortcutProperty;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.util.Util;
import burp.vocabularies.BURP;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.util.URIref;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;

@NullMarked
public abstract class TermMap extends ExpressionMap implements TermGenerator {
    @RdfProperty(Rml.datatypeMap)
    @RdfShortcutProperty(shortcutUri = Rml.datatype, targetProperty = Rml.constant)
    public @Nullable DatatypeMap datatypeMap = null;

    @RdfProperty(Rml.languageMap)
    @RdfShortcutProperty(shortcutUri = Rml.language, targetProperty = Rml.constant)
    public @Nullable LanguageMap languageMap = null;

    @RdfProperty(Rml.termType)
    public @Nullable Resource termType = null;

    @RdfProperty(Rml.gatherMap)
    public @Nullable GatherMap gatherMap = null;

    private @Nullable String baseIRI = null;

    @Override
    public Iterable<PlanNode> children() {
        List<PlanNode> children = new ArrayList<>();
        super.children().forEach(children::add);
        if (datatypeMap != null) children.add(datatypeMap);
        if (languageMap != null) children.add(languageMap);
        if (gatherMap != null) children.add(gatherMap);
        return children;
    }

    @Override
    public void collectAttributes(AttributeCollector sink) {
        super.collectAttributes(sink);
        sink.put("termType", getTermType().getLocalName(), originOf("termType"));
    }

    public abstract String getName();

    public abstract Set<Resource> getAllowedTermTypes();

    public Resource getTermType() {
        if (termType != null) {
            return termType;
        }
        return getDefaultTermType();
    }

    public Resource getDefaultTermType() {
        if (expression == null) {
            return RML.BLANKNODE;
        }
        return RML.IRI;
    }

    @Override
    public List<Term> generateTerms(@Nullable Iteration i, EvaluationContext context) {
        if (!isConditionSatisfied(i, context)) {
            return Collections.emptyList();
        }
        Set<Resource> allowed = this.getAllowedTermTypes();

        if (gatherMap != null && allowed.contains(BURP.CollectionOrContainer)) {
            if (expression == null) {
                return gatherMap.generateTerms(i, null, context);
            } else {
                var generatedIds = generateExpressionTerms(i, Set.of(RML.LITERAL), context).stream()
                        .filter(it -> it instanceof BlankNodeOrIRI)
                        .map(it -> (BlankNodeOrIRI) it)
                        .toList();
                return gatherMap.generateTerms(i, generatedIds, context);
            }
        }

        return generateExpressionTerms(i, context);
    }

    @Override
    public List<Term> generateTerms(@Nullable Iteration i) {
        return generateTerms(i, EvaluationContext.createDefault());
    }

    public List<Term> generateExpressionTerms(@Nullable Iteration i, EvaluationContext context) {
        return generateExpressionTerms(i, Collections.emptySet(), context);
    }

    @SuppressWarnings("unchecked")
    public List<Term> generateExpressionTerms(@Nullable Iteration i, Set<Resource> disallowed, EvaluationContext context) {
        if (!isConditionSatisfied(i, context)) {
            return Collections.emptyList();
        }
        Set<Resource> allowed = new HashSet<>(this.getAllowedTermTypes());
        allowed.removeAll(disallowed);
        Resource effectiveTermType = getTermType();

        if (RML.IRI.equals(effectiveTermType) && allowed.contains(RML.IRI)) {
            return (List<Term>) (List<?>) generateIRIs(i, context);
        } else if (RML.URI.equals(effectiveTermType) && allowed.contains(RML.URI)) {
            return (List<Term>) (List<?>) generateURIs(i, context);
        } else if (RML.UnsafeIRI.equals(effectiveTermType) && allowed.contains(RML.IRI)) {
            return (List<Term>) (List<?>) generateUnsafeIRIs(i, context);
        } else if (RML.BLANKNODE.equals(effectiveTermType) && allowed.contains(RML.BLANKNODE)) {
            return (List<Term>) (List<?>) generateBlankNodes(i, context);
        } else if (RML.LITERAL.equals(effectiveTermType) && allowed.contains(RML.LITERAL)) {
            return (List<Term>) (List<?>) generateLiterals(i, datatypeMap, languageMap, context);
        } else {
            RmlError error = new RmlError(
                    "The term map " + this.getName() + " generates " + effectiveTermType + ", which is not allowed. " +
                            "Expected one of " + allowed,
                    emitOrigin(),
                    RER.IncorrectTermType,
                    null,
                    Map.of(
                            RML.termType, effectiveTermType,
                            BURP.allowedTermType, String.join(" ", allowed.stream().map(Resource::getURI).toList())
                    ),
                    this
            );
            if (context.validationMode() == ValidationMode.STRICT) {
                throw new BurpException(error);
            }
            if (!context.report().getErrors().contains(error)) {
                context.report().getErrors().add(error);
            }
            return Collections.emptyList();
        }
    }

    private @Nullable IRITerm createUnsafeIRITerm(String string, EvaluationContext context, Set<LogicalTarget> targets, @Nullable OriginLineage lineage) {
        String encoded = URIref.encode(string);
        String baseIriVal = getBaseIRI(context);
        String baseEncoded = URIref.encode(baseIriVal + string);

        if (Util.isValidAndAbsoluteIRI(encoded)) {
            checkConstantTemplateSuggestion(string, context);
            return new IRITerm(string, targets, lineage);
        } else if (Util.isValidAndAbsoluteIRI(baseEncoded)) {
            checkConstantTemplateSuggestion(baseIriVal + string, context);
            return new IRITerm(baseIriVal + string, targets, lineage);
        } else {
            RmlError error = new RmlError(
                    baseIriVal + " and " + string + " do not constitute a valid UnsafeIRI",
                    expressionOrigin(),
                    RER.InvalidIRI,
                    this
            );
            if (context.validationMode() == ValidationMode.STRICT) {
                throw new BurpException(error);
            }
            if (!context.report().getErrors().contains(error)) {
                context.report().getErrors().add(error);
            }
            return null;
        }
    }

    public List<IRITerm> generateUnsafeIRIs(@Nullable Iteration i, EvaluationContext context) {
        Set<LogicalTarget> targets = getEffectiveTargets();
        OriginLineage lineage = i != null ? i.getLineage() : null;
        var tplCtx = context.withTemplateSafety(TemplateReferenceSafety.UNSAFE);
        var values = generateValues(i, tplCtx);
        List<IRITerm> result = new ArrayList<>();

        for (Object it : values) {
            if (it == null) continue;
            if (it instanceof IRITerm iri) {
                result.add(new IRITerm(iri.uri(), targets, iri.lineage() != null ? iri.lineage() : lineage));
            } else {
                String string = switch (it) {
                    case String str -> str;
                    case LiteralTerm lt -> lt.value();
                    default -> it.toString();
                };

                IRITerm term = createUnsafeIRITerm(string, context, targets, lineage);
                if (term != null) {
                    result.add(term);
                }
            }
        }
        return result;
    }

    private @Nullable IRITerm createIRITerm(String string, EvaluationContext context, Set<LogicalTarget> targets, @Nullable OriginLineage lineage) {
        String baseIriVal = getBaseIRI(context);
        if (Util.isValidAndAbsoluteIRI(string)) {
            checkConstantTemplateSuggestion(string, context);
            return new IRITerm(string, targets, lineage);
        } else if (Util.isValidAndAbsoluteIRI(baseIriVal + string)) {
            checkConstantTemplateSuggestion(baseIriVal + string, context);
            return new IRITerm(baseIriVal + string, targets, lineage);
        } else {
            RmlError error = new RmlError(
                    baseIriVal + " and " + string + " do not constitute a valid IRI",
                    expressionOrigin(),
                    RER.InvalidIRI,
                    this
            );
            if (context.validationMode() == ValidationMode.STRICT) {
                throw new BurpException(error);
            }
            if (!context.report().getErrors().contains(error)) {
                context.report().getErrors().add(error);
            }
            return null;
        }
    }

    public List<IRITerm> generateIRIs(@Nullable Iteration i, EvaluationContext context) {
        Set<LogicalTarget> targets = getEffectiveTargets();
        OriginLineage lineage = i != null ? i.getLineage() : null;
        var tplCtx = context.withTemplateSafety(TemplateReferenceSafety.SAFE_IRI);
        List<Object> values = generateValues(i, tplCtx);
        List<IRITerm> result = new ArrayList<>();

        for (Object it : values) {
            if (it == null) continue;
            if (it instanceof IRITerm iri) {
                result.add(new IRITerm(iri.uri(), targets, iri.lineage() != null ? iri.lineage() : lineage));
            } else {
                String string = (it instanceof String) ? (String) it : ((it instanceof LiteralTerm) ? ((LiteralTerm) it).value() : it.toString());
                IRITerm term = createIRITerm(string, context, targets, lineage);
                if (term != null) {
                    result.add(term);
                }
            }
        }
        return result;
    }

    public List<IRITerm> generateIRIs(@Nullable Iteration i) {
        return generateIRIs(i, EvaluationContext.createDefault());
    }

    private @Nullable IRITerm createURITerm(String string, EvaluationContext context, Set<LogicalTarget> targets, @Nullable OriginLineage lineage) {
        String baseIriVal = getBaseIRI(context);
        if (Util.isValidAndAbsoluteURI(string)) {
            checkConstantTemplateSuggestion(string, context);
            return new IRITerm(string, targets, lineage);
        } else if (Util.isValidAndAbsoluteURI(baseIriVal + string)) {
            checkConstantTemplateSuggestion(baseIriVal + string, context);
            return new IRITerm(baseIriVal + string, targets, lineage);
        } else {
            RmlError error = new RmlError(
                    baseIriVal + " and " + string + " do not constitute a valid URI",
                    expressionOrigin(),
                    RER.InvalidURI,
                    this
            );
            if (context.validationMode() == ValidationMode.STRICT) {
                throw new BurpException(error);
            }
            if (!context.report().getErrors().contains(error)) {
                context.report().getErrors().add(error);
            }
            return null;
        }
    }

    private void checkConstantTemplateSuggestion(String resolvedIri, EvaluationContext context) {
        if (this.expression instanceof Template tpl && !tpl.hasReferenceSegments()) {
            Origin origin = expressionOrigin();
            RmlError error = new RmlError(
                    "Template \"" + tpl.getTemplate() + "\" contains no reference expressions. Use rml:constant <" + resolvedIri + "> instead.",
                    origin,
                    RER.TemplateSyntaxError,
                    null,
                    Map.of(RML.constant, ResourceFactory.createResource(resolvedIri)),
                    this
            );
            if (context.validationMode() == ValidationMode.LENIENT) {
                if (!context.report().getErrors().contains(error)) {
                    context.report().getErrors().add(error);
                }
            } else if (context.validationMode() == ValidationMode.STRICT) {
                throw new BurpException(error);
            }
        }
    }

    public List<IRITerm> generateURIs(@Nullable Iteration i, EvaluationContext context) {
        Set<LogicalTarget> targets = getEffectiveTargets();
        OriginLineage lineage = i != null ? i.getLineage() : null;
        var tplCtx = context.withTemplateSafety(TemplateReferenceSafety.SAFE_URI);
        var values = generateValues(i, tplCtx);
        List<IRITerm> result = new ArrayList<>();

        for (var it : values) {
            if (it == null) continue;
            if (it instanceof IRITerm iri) {
                result.add(new IRITerm(iri.uri(), targets, iri.lineage() != null ? iri.lineage() : lineage));
            } else {
                String string = (it instanceof String) ? (String) it : it.toString();
                IRITerm term = createURITerm(string, context, targets, lineage);
                if (term != null) {
                    result.add(term);
                }
            }
        }
        return result;
    }

    public List<IRITerm> generateURIs(@Nullable Iteration i) {
        return generateURIs(i, EvaluationContext.createDefault());
    }

    protected List<BlankNodeTerm> generateBlankNodes(@Nullable Iteration i, EvaluationContext context) {
        Set<LogicalTarget> targets = getEffectiveTargets();
        OriginLineage lineage = i != null ? i.getLineage() : null;
        var tplCtx = context.withTemplateSafety(TemplateReferenceSafety.UNSAFE);

        switch (expression) {
            case RDFNodeConstant expr -> {
                if (expr.constant instanceof BlankNodeTerm constant) {
                    return Collections.singletonList(new BlankNodeTerm(constant.id(), targets, lineage));
                } else {
                    return Collections.emptyList();
                }
            }
            case Template template -> {
                return template.values(i, tplCtx).stream()
                        .filter(Objects::nonNull)
                        .map(val -> blankNodeFor(val, targets, context, lineage))
                        .toList();
            }
            case Reference reference -> {
                return reference.values(i).stream()
                        .filter(Objects::nonNull)
                        .map(val -> blankNodeFor(val, targets, context, lineage))
                        .toList();
            }
            case FunctionExecution functionExecution -> {
                return functionExecution.values(i).stream()
                        .filter(Objects::nonNull)
                        .map(val -> blankNodeFor(val, targets, context, lineage))
                        .toList();
            }
            case null -> {
                return Collections.singletonList(new BlankNodeTerm(context.blankNodeScope().allocateFresh(), targets, lineage));
            }
            default -> throw new BurpException(
                    new RmlError(
                            "Error generating blank node for expression " + expression,
                            expressionOrigin(),
                            RER.UnexpectedError,
                            this
                    )
            );
        }
    }

    protected List<BlankNodeTerm> generateBlankNodes(@Nullable Iteration i) {
        return generateBlankNodes(i, EvaluationContext.createDefault());
    }

    private BlankNodeTerm blankNodeFor(Object value, Set<LogicalTarget> targets, EvaluationContext context, @Nullable OriginLineage lineage) {
        String id = context.blankNodeScope().getOrAllocate(value);
        return new BlankNodeTerm(id, targets, lineage);
    }

    private Set<LogicalTarget> intersectTargets(Set<LogicalTarget> t1, Set<LogicalTarget> t2) {
        if (t1.isEmpty()) return t2;
        if (t2.isEmpty()) return t1;
        Set<LogicalTarget> intersection = new HashSet<>(t1);
        intersection.retainAll(t2);
        return intersection;
    }

    protected List<LiteralTerm> generateLiterals(@Nullable Iteration i, @Nullable DatatypeMap dm, @Nullable LanguageMap lm, EvaluationContext context) {
        List<IRITerm> datatypes = dm != null ? dm.generateIRIs(i, context) : null;
        List<LanguageTag> languages = lm != null ? lm.generateLanguageTags(i, context) : null;
        Set<LogicalTarget> baseTargets = getEffectiveTargets();
        OriginLineage lineage = i != null ? i.getLineage() : null;
        var tplCtx = context.withTemplateSafety(TemplateReferenceSafety.UNSAFE);

        switch (expression) {
            case RDFNodeConstant expr -> {
                if (expr.constant instanceof LiteralTerm constant) {
                    return literalFor(constant, datatypes, languages, baseTargets, lineage);
                } else if (expr.constant != null) {
                    return literalFor(expr.constant, datatypes, languages, baseTargets, lineage);
                } else {
                    return Collections.emptyList();
                }
            }
            case Template template -> {
                return template.values(i, tplCtx).stream()
                        .flatMap(val -> literalFor(val, datatypes, languages, baseTargets, lineage).stream())
                        .toList();
            }
            case Reference reference -> {
                return reference.values(i).stream()
                        .flatMap(val -> literalFor(val, datatypes, languages, baseTargets, lineage).stream())
                        .toList();
            }
            case FunctionExecution functionExecution -> {
                return functionExecution.values(i).stream()
                        .flatMap(val -> literalFor(val, datatypes, languages, baseTargets, lineage).stream())
                        .toList();
            }
            case null, default -> throw new BurpException(
                    new RmlError(
                            "Error generating literal for expression " + expression,
                            expressionOrigin(),
                            RER.UnexpectedError,
                            this
                    )
            );
        }
    }

    protected List<LiteralTerm> generateLiterals(@Nullable Iteration i, @Nullable DatatypeMap dm, @Nullable LanguageMap lm) {
        return generateLiterals(i, dm, lm, EvaluationContext.createDefault());
    }

    private List<LiteralTerm> literalFor(
            Object value,
            @Nullable List<IRITerm> datatypes,
            @Nullable List<LanguageTag> languages,
            Set<LogicalTarget> baseTargets,
            @Nullable OriginLineage lineage
    ) {
        if (value == null) {
            return Collections.emptyList();
        }

        String lexicalValue = (value instanceof LiteralTerm lt) ? lt.value() : value.toString();

        if (languages != null && !languages.isEmpty()) {
            return languages.stream()
                    .map(langTag -> new LiteralTerm(lexicalValue, null, langTag.tag(), intersectTargets(baseTargets, langTag.targets()), lineage))
                    .toList();
        }
        if (datatypes != null && !datatypes.isEmpty()) {
            return datatypes.stream()
                    .map(dt -> {
                        Term term = (value instanceof LiteralTerm) ? Datardf.toTerm(lexicalValue, dt) : Datardf.toTerm(value, dt);
                        if (term instanceof LiteralTerm lt) {
                            return new LiteralTerm(lt.value(), lt.datatype(), lt.language(), intersectTargets(baseTargets, dt.targets()), lineage);
                        }
                        return new LiteralTerm(lexicalValue, dt, null, intersectTargets(baseTargets, dt.targets()), lineage);
                    })
                    .toList();
        }

        if (value instanceof LiteralTerm lt) {
            return List.of(new LiteralTerm(lt.value(), lt.datatype(), lt.language(), baseTargets, lineage));
        }

        Term term = Datardf.toTerm(value);
        if (term instanceof LiteralTerm lt) {
            return List.of(new LiteralTerm(lt.value(), lt.datatype(), lt.language(), baseTargets, lineage));
        } else {
            return Collections.singletonList(new LiteralTerm(lexicalValue, null, null, baseTargets, lineage));
        }
    }

    protected String getBaseIRI(EvaluationContext context) {
        if (context != null && context.baseIri() != null) {
            return context.baseIri();
        }
        return getBaseIRI();
    }

    protected String getBaseIRI() {
        if (baseIRI == null) {
            BaseIRIScope scope = ancestor(BaseIRIScope.class);
            if (scope != null) {
                baseIRI = scope.getBaseIri();
            }
        }
        return baseIRI != null ? baseIRI : "";
    }
}
