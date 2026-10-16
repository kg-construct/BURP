package burp.dev.rml.service.validator;

import burp.dev.rml.service.plan.PlanNodeDescriptor;
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
import burp.reporting.StatementPart;
import burp.parse.turtleprov.ProvTurtleVisitor;
import burp.reporting.PointRange;
import burp.vocabularies.RER;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
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
public class FunctionExecutionValidator {

    public void validate(List<PlanNode> allNodes, List<RmlDiagnostic> diagnostics) {
        validate(allNodes, diagnostics, Collections.emptyMap());
    }

    public void validate(List<PlanNode> allNodes, List<RmlDiagnostic> diagnostics, @Nullable Map<String, String> prefixMap) {
        for (PlanNode node : allNodes) {
            if (node instanceof FunctionExecution fe) {
                Range feRange = PlanNodeDescriptor.getFirstRangeForNode(fe);
                if (feRange == null) continue;

                Range funcRange = null;
                Origin funcOrigin = fe.emitOrigin("functionMap", StatementPart.Object);
                if (funcOrigin.sourceStatements() != null) {
                    List<PointRange> ranges = ProvTurtleVisitor.retrieveTurtleLocation(funcOrigin.sourceStatements());
                    if (!ranges.isEmpty()) funcRange = ranges.getFirst().toLsp();
                }
                if (funcRange == null) funcRange = feRange;

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
                        diagnostics.add(new RmlDiagnostic(
                                funcRange,
                                DiagnosticSeverity.Warning,
                                RER.UnknownFunction.getURI(),
                                "Unknown or unimplemented function: " + funcDisplay + ".",
                                "BURP Validator"
                        ));
                    }
                }

                // 2. Validate parameter bindings (duplicates & ontology parameter validity)
                Set<Resource> boundParams = new HashSet<>();
                for (Input in : fe.inputs) {
                    Range inRange = null;
                    if (in.parameterMap != null) {
                        Origin inOrigin = in.emitOrigin("parameterMap", StatementPart.Object);
                        if (inOrigin.sourceStatements() != null) {
                            List<PointRange> ranges = ProvTurtleVisitor.retrieveTurtleLocation(inOrigin.sourceStatements());
                            if (!ranges.isEmpty()) inRange = ranges.getFirst().toLsp();
                        }
                        if (inRange == null) inRange = PlanNodeDescriptor.getFirstRangeForNode(in.parameterMap);
                    }
                    if (inRange == null) inRange = feRange;

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
                            diagnostics.add(new RmlDiagnostic(
                                    inRange,
                                    DiagnosticSeverity.Error,
                                    RER.DuplicateParameter.getURI(),
                                    "Duplicate parameter binding: parameter " + paramDisplay + " is bound multiple times.",
                                    "BURP Validator"
                            ));
                        }

                        // Check valid parameter defined by ontology and warn if using predicate IRI instead of parameter entity IRI
                        if (funcResource != null && FnmlOntologyRegistry.isDeclaredFunction(funcResource)) {
                            FnmlParameter matched = FnmlOntologyRegistry.findMatchingParameter(funcResource, paramResource);
                            if (matched == null) {
                                diagnostics.add(new RmlDiagnostic(
                                        inRange,
                                        DiagnosticSeverity.Warning,
                                        RER.InvalidParameter.getURI(),
                                        "Parameter " + paramDisplay + " is not a recognized parameter for function " + funcDisplay + ".",
                                        "BURP Validator"
                                ));
                            } else if (matched.isUsingPredicateWhenParamDefined(paramResource)) {
                                String paramDefDisplay = RmlEvaluationResultFormatter.compactIri(matched.iri().getURI(), prefixMap);
                                diagnostics.add(new RmlDiagnostic(
                                        inRange,
                                        DiagnosticSeverity.Warning,
                                        RER.UnknownFunctionParameterIRI.getURI(),
                                        "Parameter " + paramDisplay + " is a predicate IRI; consider using its parameter definition IRI " + paramDefDisplay + " instead.",
                                        "BURP Validator"
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
                        diagnostics.add(new RmlDiagnostic(
                                feRange,
                                DiagnosticSeverity.Error,
                                RER.MissingParameter.getURI(),
                                "Missing required parameter " + missingParam.name() + " (" + missingParamDisplay + ") for function " + funcDisplay + ".",
                                "BURP Validator"
                        ));
                    }
                }

                // 4. Validate return map if defined
                if (fe.returnMap != null) {
                    Range returnRange = null;
                    Origin returnOrigin = fe.emitOrigin("returnMap", StatementPart.Object);
                    if (returnOrigin.sourceStatements() != null) {
                        List<PointRange> ranges = ProvTurtleVisitor.retrieveTurtleLocation(returnOrigin.sourceStatements());
                        if (!ranges.isEmpty()) returnRange = ranges.getFirst().toLsp();
                    }
                    if (returnRange == null) returnRange = PlanNodeDescriptor.getFirstRangeForNode(fe.returnMap);
                    if (returnRange == null) returnRange = feRange;

                    Resource returnResource = null;
                    if (fe.returnMap.expression instanceof RDFNodeConstant c && c.constant instanceof IRITerm iriTerm) {
                        returnResource = ResourceFactory.createResource(iriTerm.uri());
                    }

                    if (returnResource != null && funcResource != null && FnmlOntologyRegistry.isDeclaredFunction(funcResource)) {
                        String returnDisplay = RmlEvaluationResultFormatter.compactIri(returnResource.getURI(), prefixMap);
                        FnmlOutput matchedOutput = FnmlOntologyRegistry.findMatchingOutput(funcResource, returnResource);
                        if (matchedOutput == null) {
                            diagnostics.add(new RmlDiagnostic(
                                    returnRange,
                                    DiagnosticSeverity.Warning,
                                    RER.UnknownFunctionReturnIRI.getURI(),
                                    "Return " + returnDisplay + " is not a recognized return output for function " + funcDisplay + ".",
                                    "BURP Validator"
                            ));
                        } else if (matchedOutput.isUsingPredicateWhenOutputDefined(returnResource)) {
                            String returnDefDisplay = RmlEvaluationResultFormatter.compactIri(matchedOutput.iri().getURI(), prefixMap);
                            diagnostics.add(new RmlDiagnostic(
                                    returnRange,
                                    DiagnosticSeverity.Warning,
                                    RER.UnknownFunctionReturnIRI.getURI(),
                                    "Return " + returnDisplay + " is an output predicate IRI; consider using its return definition IRI " + returnDefDisplay + " instead.",
                                    "BURP Validator"
                            ));
                        }
                    }
                }
            }
        }
    }
}
