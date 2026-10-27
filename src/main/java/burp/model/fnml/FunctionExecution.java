package burp.model.fnml;

import burp.model.*;
import burp.model.context.EvaluationContext;
import burp.model.key.AstKey;
import burp.model.rdf.IRITerm;
import burp.parse.loader.annotations.*;
import burp.reporting.*;
import burp.vocabularies.RER;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;

@RdfType(Rml.FunctionExecution)
@RdfWrapped(Rml.functionExecution)
public class FunctionExecution extends AbstractPlanNode implements Expression {
    @RdfId
    public Resource subject;

    @Override
    public Resource getIri() { return subject; }

    @RdfProperty(Rml.functionMap)
    @RdfShortcutProperty(shortcutUri = Rml.function, targetProperty = Rml.constant)
    @NonNull
    public FunctionMap functionMap;

    @RdfProperty(Rml.input)
    public List<Input> inputs = new ArrayList<>();

    @RdfProperty(Rml.returnMap)
    @RdfShortcutProperty(shortcutUri = Rml.return_, targetProperty = Rml.constant)
    @Nullable
    public ReturnMap returnMap = null;

    @Override
    public AstKey computeAstKey() {
        String fn = (functionMap != null && functionMap.expression instanceof RDFNodeConstant c && c.constant != null)
                ? c.constant.toString()
                : (functionMap != null ? String.valueOf(functionMap.getAstKey().hashCode()) : "");
        List<AstKey> inputKeys = new ArrayList<>();
        for (Input in : inputs) {
            inputKeys.add(in.getAstKey());
        }
        return new AstKey.FunctionExecutionKey(fn, inputKeys);
    }

    @Override
    public List<PlanNode> children() {
        List<PlanNode> list = new ArrayList<>();
        if (functionMap != null) list.add(functionMap);
        list.addAll(inputs);
        if (returnMap != null) list.add(returnMap);
        return list;
    }

    @Override
    public List<PlanNode> dependencies() {
        return children();
    }

    public List<Object> values(@Nullable Iteration iteration, EvaluationContext context) {
        List<Object> list = new ArrayList<>();

        if (functionMap == null) {
            throw new BurpException(new RmlError(
                    "FunctionExecution missing required functionMap.",
                    emitOrigin("functionMap"),
                    RER.FunctionExecutionError,
                    this
            ));
        }
        List<IRITerm> functions = functionMap.generateIRIs(iteration, context);
        if (functions.size() != 1) {
            throw new BurpException(new RmlError(
                    "Function map should generate exactly one value.",
                    emitOrigin("functionMap"),
                    RER.FunctionExecutionError,
                    this
            ));
        }

        String functionUri = functions.getFirst().uri();

        Map<String, Object> inputMap = new HashMap<>();

        for (Input input : inputs) {
            List<IRITerm> parameters = input.parameterMap.generateIRIs(iteration, context);
            if (parameters.size() != 1) {
                throw new BurpException(new RmlError(
                        "Parameter map should generate exactly one value.",
                        input.emitOrigin("parameterMap"),
                        RER.FunctionExecutionError,
                        input
                ));
            }

            String parameterUri = parameters.getFirst().uri();

            var generatedInputs = input.inputValueMap.generateTerms(iteration, context);
            if (generatedInputs.size() != 1) {
                throw new BurpException(new RmlError(
                        "Input value map should generate exactly one value.",
                        input.emitOrigin("inputValueMap"),
                        RER.FunctionExecutionError,
                        input
                ));
            }

            // FIXME FNML 6.1 we should call the functions for each parameter value
            Object inputValue = generatedInputs.getFirst();
            inputMap.put(parameterUri, inputValue);

            Resource funcRes = ResourceFactory.createResource(functionUri);
            Resource paramRes = ResourceFactory.createResource(parameterUri);
            FnmlParameter matched = FnmlOntologyRegistry.findMatchingParameter(funcRes, paramRes);
            if (matched != null) {
                inputMap.put(matched.iri().getURI(), inputValue);
                if (matched.predicateIri() != null) {
                    inputMap.put(matched.predicateIri().getURI(), inputValue);
                }
            }
        }

        Origin originCall = originOf("functionMap");
        if (originCall == null) originCall = originOf("function");
        if (originCall == null) originCall = emitOrigin();

        List<Return> results = FunctionsRegistry.execute(functionUri, inputMap, originCall, context);
        for (Return o : results) {
            Origin originReturnMap = emitOrigin("returnMap");
            if (returnMap == null) {
                list.add(o.defaultValue);
            } else {
                List<IRITerm> returns = returnMap.generateIRIs(iteration, context);
                if (returns.size() != 1) {
                    throw new BurpException(new RmlError(
                            "Return map should generate exactly one value.",
                            originReturnMap,
                            RER.FunctionExecutionError,
                            this
                    ));
                }

                String returnUri = returns.getFirst().uri();
                Object v = o.get(returnUri);
                if (v == null) {
                    Resource funcRes = ResourceFactory.createResource(functionUri);
                    Resource retRes = ResourceFactory.createResource(returnUri);
                    FnmlOutput matchedOut = FnmlOntologyRegistry.findMatchingOutput(funcRes, retRes);
                    if (matchedOut != null) {
                        v = o.get(matchedOut.iri().getURI());
                        if (v == null && matchedOut.predicateIri() != null) {
                            v = o.get(matchedOut.predicateIri().getURI());
                        }
                    }
                }
                list.add(v);
            }
        }

        return list;
    }

    public List<Object> values(Iteration iteration) {
        return values(iteration, EvaluationContext.createDefault());
    }

    @Override
    public List<RmlEvaluationResult> evaluateNode(Iteration iter, @NonNull EvaluationContext context) {
        List<RmlEvaluationResult> resList = new ArrayList<>();
        for (Object obj : values(iter, context)) {
            if (obj instanceof RmlEvaluationResult r) {
                resList.add(r);
            } else if (obj != null) {
                resList.add(new RawValue(obj));
            }
        }
        return resList;
    }
}
