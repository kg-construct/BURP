package burp.model;

import burp.model.fnmlutil.FunctionsRegistry;
import burp.model.rdf.IRITerm;
import burp.parse.turtleprov.ProvTurtleVisitor;
import burp.reporting.*;
import burp.vocabularies.RER;

import java.util.*;

public class FunctionExecution implements Expression {
    private PlanNode parent = null;
    public FunctionMap functionMap = null;
    public List<Input> inputs = new ArrayList<>();
    public ReturnMap returnMap = null;

    public StatementParts callStmt;
    public List<StatementParts> inputsStmt = new ArrayList<>();
    public StatementParts returnMapStmt = null;
    public StatementParts functionMapStmt = null;

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
        List<PlanNode> list = new ArrayList<>();
        if (functionMap != null) list.add(functionMap);
        for (Input input : inputs) {
            if (input.parameterMap != null) list.add(input.parameterMap);
            if (input.inputValueMap != null) list.add(input.inputValueMap);
        }
        if (returnMap != null) list.add(returnMap);
        return list;
    }

    @Override
    public List<PlanNode> dependencies() {
        return Collections.emptyList();
    }

    @Override
    public List<PointRange> nodeRanges() {
        List<RDFGraphPointer> pointers = new ArrayList<>();
        pointers.add(callStmt);
        if (functionMapStmt != null) pointers.add(functionMapStmt);
        if (returnMapStmt != null) pointers.add(returnMapStmt);
        pointers.addAll(inputsStmt);
        return ProvTurtleVisitor.retrieveTurtleLocation(pointers);
    }

    public List<Object> values(Iteration iteration) {
        List<Object> list = new ArrayList<>();

        List<IRITerm> functions = functionMap.generateIRIs(iteration);
        if (functions.size() != 1) {
            throw new BurpException(new RmlError(
                "Function map should generate exactly one value.",
                new Origin(this, functionMapStmt != null ? List.of(functionMapStmt) : List.of()),
                RER.FunctionExecutionError
            ));
        }

        String functionUri = functions.get(0).uri();

        Map<String, Object> map = new HashMap<>();

        for (int i = 0; i < inputs.size(); i++) {
            Input input = inputs.get(i);
            List<IRITerm> parameters = input.parameterMap.generateIRIs(iteration);
            if (parameters.size() != 1) {
                throw new BurpException(new RmlError(
                    "Parameter map should generate exactly one value.",
                    new Origin(this, List.of(inputsStmt.get(i))),
                    RER.FunctionExecutionError
                ));
            }

            String parameterUri = parameters.getFirst().uri();

            var generatedInputs = input.inputValueMap.generateTerms(iteration);
            if (generatedInputs.size() != 1) {
                throw new BurpException(new RmlError(
                    "Input value map should generate exactly one value.",
                    new Origin(this, List.of(inputsStmt.get(i))),
                    RER.FunctionExecutionError
                ));
            }

            map.put(parameterUri, generatedInputs.getFirst());
        }

        Origin originCall = new Origin(this, List.of(callStmt));

        List<Return> results = FunctionsRegistry.execute(functionUri, map, originCall);
        for (Return o : results) {
            Origin originReturnMap = new Origin(this, returnMapStmt != null ? List.of(returnMapStmt) : List.of());
            if (returnMap == null) {
                list.add(o.defaultValue);
            } else {
                List<IRITerm> returns = returnMap.generateIRIs(iteration);
                if (returns.size() != 1) {
                    throw new BurpException(new RmlError(
                        "Return map should generate exactly one value.",
                        originReturnMap,
                        RER.FunctionExecutionError
                    ));
                }

                String returnUri = returns.get(0).uri();
                Object v = o.get(returnUri);
                list.add(v);
            }
        }

        return list;
    }
}