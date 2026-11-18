package burp.dev.rml.service.validator;

import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.eval.EvaluationContexts;
import burp.dev.rml.service.eval.EvaluationDataContext;
import burp.dev.rml.service.eval.RangeDataContext;
import burp.dev.rml.service.eval.RmlEvaluator;
import burp.dev.rml.service.ontology.OntologyConformanceChecker;
import burp.dev.rml.service.ontology.OntologyRegistry;
import burp.model.*;
import burp.model.context.EvaluationContext;
import burp.model.rdf.IRITerm;
import burp.model.rdf.LiteralTerm;
import burp.model.rdf.Term;
import burp.parse.ValidationMode;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;

/// Performs sampled dynamic ontology conformance checks on evaluated predicate-object pairs.
@NullMarked
public class RuntimeOntologyValidator implements MappingValidator {

    public static final String ID = "runtime-ontology";
    private static final int DEFAULT_SAMPLE_LIMIT = 10;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getSourceName() {
        return "BURP Runtime Ontology Validator";
    }

    @Override
    public ValidationExecutionPolicy getExecutionPolicy() {
        return ValidationExecutionPolicy.DEBOUNCED_ASYNC;
    }

    @Override
    public List<RmlError> validate(ValidationContext context) {
        List<RmlError> errors = new ArrayList<>();
        MappingDocument doc = context.loaded().document();
        if (doc.triplesMaps.isEmpty()) {
            return errors;
        }

        MappingWorkspace workspace = context.workspace();
        OntologyRegistry registry = workspace.getOntologyRegistry(context.loaded().model(), context.mappingFileUri());
        Map<String, String> prefixMap = new HashMap<>(doc.prefixMap);
        if (context.loaded().model() != null) {
            prefixMap.putAll(context.loaded().model().getNsPrefixMap());
        }

        Integer configuredLimit = workspace.getIterationSampleLimit();
        int sampleLimit = (configuredLimit != null && configuredLimit > 0) ? configuredLimit : DEFAULT_SAMPLE_LIMIT;
        EvaluationDataContext sampleContext = new RangeDataContext(0, sampleLimit - 1);
        RmlEvaluator evaluator = new RmlEvaluator(workspace.getCache());

        for (TriplesMap tm : doc.triplesMaps) {
            if (context.isCancelled()) {
                return errors;
            }
            validateTriplesMap(tm, context, evaluator, sampleContext, registry, prefixMap, errors);
        }

        return errors;
    }

    private void validateTriplesMap(
            TriplesMap tm,
            ValidationContext validationContext,
            RmlEvaluator evaluator,
            EvaluationDataContext sampleContext,
            OntologyRegistry registry,
            Map<String, String> prefixMap,
            List<RmlError> errors
    ) {
        List<PredicateObjectPair> dynamicPairs = collectDynamicPairs(tm);
        if (dynamicPairs.isEmpty()) {
            return;
        }

        List<Iteration> iterations;
        try {
            iterations = evaluator.resolveIterations(sampleContext, tm);
        } catch (Exception e) {
            return;
        }
        if (iterations.isEmpty() && tm.logicalSource == null) {
            iterations = Collections.singletonList(null);
        }
        if (iterations.isEmpty()) {
            return;
        }

        Set<Resource> subjectClasses = tm.subjectMap != null
                ? new LinkedHashSet<>(tm.subjectMap.classes)
                : Collections.emptySet();

        String baseIri = EvaluationContexts.resolveBaseIri(tm, validationContext.workspace().getBaseIri());
        EvaluationContext evalContext = EvaluationContext.createDefault()
                .withBaseIri(baseIri)
                .withValidationMode(ValidationMode.LENIENT);

        for (Iteration iter : iterations) {
            if (validationContext.isCancelled()) {
                return;
            }
            for (PredicateObjectPair pair : dynamicPairs) {
                if (validationContext.isCancelled()) {
                    return;
                }
                checkPairForIteration(pair, iter, evalContext, evaluator, subjectClasses, registry, prefixMap, errors);
            }
        }
    }

    private void checkPairForIteration(
            PredicateObjectPair pair,
            @Nullable Iteration iter,
            EvaluationContext evalContext,
            RmlEvaluator evaluator,
            Set<Resource> subjectClasses,
            OntologyRegistry registry,
            Map<String, String> prefixMap,
            List<RmlError> errors
    ) {
        List<RmlEvaluationResult> predResults = evaluator.evaluateNodeSafe(pair.pm(), iter, evalContext);
        if (predResults.isEmpty()) {
            return;
        }
        List<RmlEvaluationResult> objResults = evaluator.evaluateNodeSafe(pair.om(), iter, evalContext);
        if (objResults.isEmpty()) {
            return;
        }

        for (RmlEvaluationResult pRes : predResults) {
            if (!(pRes instanceof IRITerm predIri)) {
                continue;
            }
            Resource predicate = ResourceFactory.createResource(predIri.uri());

            for (RmlEvaluationResult oRes : objResults) {
                if (!(oRes instanceof Term objTerm)) {
                    continue;
                }

                Resource termType;
                String lexical = null;
                Resource litDatatype = null;

                if (objTerm instanceof LiteralTerm lit) {
                    termType = RML.Literal;
                    lexical = lit.value();
                    if (lit.datatype() != null) {
                        litDatatype = ResourceFactory.createResource(lit.datatype().uri());
                    }
                } else if (objTerm instanceof IRITerm) {
                    termType = RML.IRI;
                } else {
                    termType = RML.BlankNode;
                }

                List<OntologyConformanceChecker.Issue> issues = OntologyConformanceChecker.check(
                        subjectClasses,
                        predicate,
                        termType,
                        null,
                        lexical,
                        litDatatype,
                        registry,
                        prefixMap
                );

                if (issues.isEmpty()) {
                    continue;
                }

                OriginLineage lineage = iter != null ? iter.getLineage() : null;
                if (predIri.lineage() != null) {
                    lineage = lineage != null ? lineage.merge(predIri.lineage()) : predIri.lineage();
                }
                if (objTerm.lineage() != null) {
                    lineage = lineage != null ? lineage.merge(objTerm.lineage()) : objTerm.lineage();
                }
                RdfPredicateObject evalResult = new RdfPredicateObject(predIri, objTerm, null, Collections.emptySet());

                for (var issue : issues) {
                    @Nullable Origin issueOrigin = issue.errorType().equals(RER.OntologyDomainMismatch)
                            ? pair.pmOrigin()
                            : (pair.omOrigin() != null ? pair.omOrigin() : pair.pmOrigin());
                    errors.add(new RmlError(
                            issue.message(),
                            issueOrigin,
                            issue.errorType(),
                            null,
                            null,
                            issue.errorType().equals(RER.OntologyDomainMismatch) ? pair.pm() : pair.om(),
                            lineage,
                            evalResult
                    ));
                }
            }
        }
    }

    private List<PredicateObjectPair> collectDynamicPairs(TriplesMap tm) {
        List<PredicateObjectPair> pairs = new ArrayList<>();
        for (PredicateObjectMap pom : tm.predicateObjectMaps) {
            for (PredicateMap pm : pom.predicateMaps) {
                Origin pmOrigin = MappingValidator.resolveNodeOrigin(pm, "constant");
                for (BaseObjectMap om : pom.objectMaps) {
                    if (isStaticConstantPair(pm, om)) {
                        continue;
                    }
                    Origin omOrigin = MappingValidator.resolveNodeOrigin(om, "constant");
                    pairs.add(new PredicateObjectPair(pm, om, pmOrigin, omOrigin));
                }
            }
        }
        return pairs;
    }

    /// Returns true if both predicate and object maps are purely constant and already checked by OntologyValidator.
    public static boolean isStaticConstantPair(PredicateMap pm, BaseObjectMap bom) {
        if (!(pm.expression instanceof RDFNodeConstant)) {
            return false;
        }
        if (bom instanceof ReferencingObjectMap) {
            return true;
        }
        if (bom instanceof TermMap tmMap) {
            boolean constantExpr = tmMap.expression instanceof RDFNodeConstant;
            boolean constantDatatype = tmMap.datatypeMap == null || tmMap.datatypeMap.expression instanceof RDFNodeConstant;
            return constantExpr && constantDatatype;
        }
        return false;
    }

    private record PredicateObjectPair(
            PredicateMap pm,
            BaseObjectMap om,
            @Nullable Origin pmOrigin,
            @Nullable Origin omOrigin
    ) {}
}
