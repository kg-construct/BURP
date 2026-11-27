package burp.dev.rml.service.validator;

import burp.dev.rml.service.RmlEvaluationResultFormatter;
import burp.model.Input;
import burp.model.PlanNode;
import burp.model.RDFNodeConstant;
import burp.model.fnml.FnmlOntologyRegistry;
import burp.model.fnml.FnmlOutput;
import burp.model.fnml.FnmlParameter;
import burp.model.fnml.FunctionExecution;
import burp.model.fnml.FunctionsRegistry;
import burp.model.rdf.IRITerm;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.RER;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Validates FnO function executions in RML mappings:
 * - Warns on unknown or unimplemented functions.
 * - Errors on duplicate parameter bindings.
 * - Warns on parameters not defined in ontology or when raw entity IRI is used instead of predicate.
 * - Errors on missing required parameters defined in the ontology.
 */
@NullMarked
public class FunctionExecutionValidator implements MappingValidator {

    public static final String ID = "fno";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public ValidationExecutionPolicy getExecutionPolicy() {
        return ValidationExecutionPolicy.IMMEDIATE;
    }

    @Override
    public List<RmlError> validate(ValidationContext context) {
        List<RmlError> errors = new ArrayList<>();
        List<PlanNode> allNodes = new ArrayList<>();
        context.workspace().collectAllNodes(context.loaded().document(), allNodes);
        Map<String, String> prefixMap = new HashMap<>(context.loaded().document().prefixMap);
        if (context.loaded().model() != null) {
            prefixMap.putAll(context.loaded().model().getNsPrefixMap());
        }
        validate(allNodes, errors, prefixMap);
        return errors;
    }

    public void validate(List<PlanNode> allNodes, List<RmlError> errors) {
        validate(allNodes, errors, Collections.emptyMap());
    }

    public void validate(List<PlanNode> allNodes, List<RmlError> errors, @Nullable Map<String, String> prefixMap) {
        for (PlanNode node : allNodes) {
            if (node instanceof FunctionExecution fe) {
                Origin feOrigin = MappingValidator.firstNodeRangeOrigin(fe);
                if (feOrigin == null) continue;

                Origin funcOrigin = MappingValidator.resolveNodeOrigin(fe, "functionMap");
                if (funcOrigin == null) funcOrigin = feOrigin;

                String functionUri = null;
                Resource funcResource = null;
                if (fe.functionMap != null && fe.functionMap.expression instanceof RDFNodeConstant c) {
                    if (c.constant instanceof IRITerm iriTerm) {
                        functionUri = iriTerm.uri();
                        funcResource = ResourceFactory.createResource(functionUri);
                    }
                }

                String funcDisplay = functionUri != null ? RmlEvaluationResultFormatter.compactIri(functionUri, prefixMap) : "<>";

                // 1. Function existence check against FunctionsRegistry (implemented functions)
                if (functionUri != null) {
                    boolean isImplemented = FunctionsRegistry.functions.containsKey(functionUri);
                    if (!isImplemented) {
                        errors.add(new RmlError(
                                "Unknown or unimplemented function: " + funcDisplay + ".",
                                funcOrigin,
                                RER.UnknownFunction
                        ));
                    }
                }

                // 2. Validate parameter bindings (duplicates & ontology parameter validity)
                Set<Resource> boundParams = new HashSet<>();
                for (Input in : fe.inputs) {
                    Origin inOrigin = null;
                    if (in.parameterMap != null) {
                        inOrigin = MappingValidator.resolveNodeOrigin(in, "parameterMap");
                        if (inOrigin == null) inOrigin = MappingValidator.firstNodeRangeOrigin(in.parameterMap);
                    }
                    if (inOrigin == null) inOrigin = feOrigin;

                    Resource paramResource = null;
                    if (in.parameterMap != null && in.parameterMap.expression instanceof RDFNodeConstant c) {
                        if (c.constant instanceof IRITerm iriTerm) {
                            paramResource = ResourceFactory.createResource(iriTerm.uri());
                        }
                    }

                    if (paramResource != null) {
                        String paramDisplay = RmlEvaluationResultFormatter.compactIri(paramResource.getURI(), prefixMap);

                        // Check duplicate parameter bindings
                        if (!boundParams.add(paramResource)) {
                            errors.add(new RmlError(
                                    "Duplicate parameter binding: parameter " + paramDisplay + " is bound multiple times.",
                                    inOrigin,
                                    RER.DuplicateParameter
                            ));
                        }

                        // Check valid parameter defined by ontology and warn if using predicate IRI instead of parameter entity IRI
                        if (funcResource != null && FnmlOntologyRegistry.isDeclaredFunction(funcResource)) {
                            FnmlParameter matched = FnmlOntologyRegistry.findMatchingParameter(funcResource, paramResource);
                            if (matched == null) {
                                errors.add(new RmlError(
                                        "Parameter " + paramDisplay + " is not a recognized parameter for function " + funcDisplay + ".",
                                        inOrigin,
                                        RER.InvalidParameter
                                ));
                            } else if (matched.isUsingPredicateWhenParamDefined(paramResource)) {
                                String paramDefDisplay = RmlEvaluationResultFormatter.compactIri(matched.iri().getURI(), prefixMap);
                                errors.add(new RmlError(
                                        "Parameter " + paramDisplay + " is a predicate IRI; consider using its parameter definition IRI " + paramDefDisplay + " instead.",
                                        inOrigin,
                                        RER.UnknownFunctionParameterIRI
                                ));
                            }
                        }
                    }
                }

                // 3. Validate missing required parameters
                if (funcResource != null && FnmlOntologyRegistry.isDeclaredFunction(funcResource)) {
                    List<FnmlParameter> missing = FnmlOntologyRegistry.getMissingRequiredParameters(funcResource, boundParams);
                    for (FnmlParameter missingParam : missing) {
                        String missingParamDisplay = RmlEvaluationResultFormatter.compactIri(missingParam.iri().getURI(), prefixMap);
                        errors.add(new RmlError(
                                "Missing required parameter " + missingParam.name() + " (" + missingParamDisplay + ") for function " + funcDisplay + ".",
                                feOrigin,
                                RER.MissingParameter
                        ));
                    }
                }

                // 4. Validate return map if defined
                if (fe.returnMap != null) {
                    Origin returnOrigin = MappingValidator.resolveNodeOrigin(fe, "returnMap");
                    if (returnOrigin == null) returnOrigin = MappingValidator.firstNodeRangeOrigin(fe.returnMap);
                    if (returnOrigin == null) returnOrigin = feOrigin;

                    Resource returnResource = null;
                    if (fe.returnMap.expression instanceof RDFNodeConstant c && c.constant instanceof IRITerm iriTerm) {
                        returnResource = ResourceFactory.createResource(iriTerm.uri());
                    }

                    if (returnResource != null && funcResource != null && FnmlOntologyRegistry.isDeclaredFunction(funcResource)) {
                        String returnDisplay = RmlEvaluationResultFormatter.compactIri(returnResource.getURI(), prefixMap);
                        FnmlOutput matchedOutput = FnmlOntologyRegistry.findMatchingOutput(funcResource, returnResource);
                        if (matchedOutput == null) {
                            errors.add(new RmlError(
                                    "Return " + returnDisplay + " is not a recognized return output for function " + funcDisplay + ".",
                                    returnOrigin,
                                    RER.UnknownFunctionReturnIRI
                            ));
                        } else if (matchedOutput.isUsingPredicateWhenOutputDefined(returnResource)) {
                            String returnDefDisplay = RmlEvaluationResultFormatter.compactIri(matchedOutput.iri().getURI(), prefixMap);
                            errors.add(new RmlError(
                                    "Return " + returnDisplay + " is an output predicate IRI; consider using its return definition IRI " + returnDefDisplay + " instead.",
                                    returnOrigin,
                                    RER.UnknownFunctionReturnIRI
                            ));
                        }
                    }
                }
            }
        }
    }
}
