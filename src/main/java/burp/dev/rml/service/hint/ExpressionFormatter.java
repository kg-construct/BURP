package burp.dev.rml.service.hint;

import burp.dev.rml.service.RmlEvaluationResultFormatter;
import burp.model.Expression;
import burp.model.Input;
import burp.model.JoinCondition;
import burp.model.RDFNodeConstant;
import burp.model.Reference;
import burp.model.Template;
import burp.model.fnml.FnmlOntologyRegistry;
import burp.model.fnml.FnmlParameter;
import burp.model.fnml.FunctionExecution;
import burp.model.rdf.IRITerm;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/// Formats AST [Expression] nodes into compact, static syntax representations.
///
/// Used for interline function syntax hints above function executions
/// and join condition summaries.
@NullMarked
public class ExpressionFormatter {

    /// Formats an arbitrary [Expression] into a compact static string.
    public static String format(@Nullable Expression expr, @Nullable Map<String, String> prefixMap) {
        return switch (expr) {
            case null -> "";
            case RDFNodeConstant c -> formatConstant(c, prefixMap);
            case Reference r -> formatReference(r);
            case Template t -> formatTemplate(t);
            case FunctionExecution fe -> formatFunctionExecution(fe, prefixMap);
            default -> expr.toString();
        };
    }

    /// Formats a [JoinCondition] into a summary expression (`childExpr == parentExpr`).
    public static String formatJoinCondition(JoinCondition jc, @Nullable Map<String, String> prefixMap) {
        String child = jc.childMap != null ? format(jc.childMap.expression, prefixMap) : "?";
        String parent = jc.parentMap != null ? format(jc.parentMap.expression, prefixMap) : "?";
        return child + " == " + parent;
    }

    /// Formats a [FunctionExecution] into compact call syntax: `funcName(param = expr, ...)`.
    public static String formatFunctionExecution(FunctionExecution fe, @Nullable Map<String, String> prefixMap) {
        String fnName = "<>";
        Resource funcResource = null;
        if (fe.functionMap != null && fe.functionMap.expression != null) {
            if (fe.functionMap.expression instanceof RDFNodeConstant c && c.constant instanceof IRITerm iri) {
                funcResource = ResourceFactory.createResource(iri.uri());
                String declaredName = FnmlOntologyRegistry.getFunctionName(funcResource);
                fnName = (declaredName != null && !declaredName.isBlank())
                        ? declaredName
                        : RmlEvaluationResultFormatter.compactIri(iri.uri(), prefixMap);
            } else {
                fnName = format(fe.functionMap.expression, prefixMap);
            }
        }

        List<Input> sortedInputs = sortInputsByDefinitionOrder(fe.inputs, funcResource);

        List<String> paramBindings = new ArrayList<>();
        for (Input in : sortedInputs) {
            String paramName = formatParameterName(in, funcResource, prefixMap);
            Expression valueExpr = in.inputValueMap != null ? in.inputValueMap.expression : null;
            String formattedValue = format(valueExpr, prefixMap);
            paramBindings.add(paramName + " = " + formattedValue);
        }

        return fnName + "(" + String.join(", ", paramBindings) + ")";
    }

    private static List<Input> sortInputsByDefinitionOrder(List<Input> inputs, @Nullable Resource funcResource) {
        if (funcResource == null || inputs.size() <= 1) {
            return inputs;
        }

        List<FnmlParameter> declaredParams = FnmlOntologyRegistry.getParameters(funcResource);
        if (declaredParams.isEmpty()) {
            return inputs;
        }

        List<Input> sorted = new ArrayList<>(inputs);
        sorted.sort(Comparator.comparingInt(in -> getParameterDefinitionIndex(in, declaredParams)));
        return sorted;
    }

    private static int getParameterDefinitionIndex(Input in, List<FnmlParameter> declaredParams) {
        if (in.parameterMap != null && in.parameterMap.expression instanceof RDFNodeConstant c && c.constant instanceof IRITerm iri) {
            Resource paramRes = ResourceFactory.createResource(iri.uri());
            for (int i = 0; i < declaredParams.size(); i++) {
                if (declaredParams.get(i).matches(paramRes)) {
                    return i;
                }
            }
        }
        return Integer.MAX_VALUE;
    }

    private static String formatParameterName(Input in, @Nullable Resource funcResource, @Nullable Map<String, String> prefixMap) {
        if (in.parameterMap == null || in.parameterMap.expression == null) {
            return "?";
        }
        if (in.parameterMap.expression instanceof RDFNodeConstant c && c.constant instanceof IRITerm iri) {
            Resource paramRes = ResourceFactory.createResource(iri.uri());
            FnmlParameter declared = funcResource != null
                    ? FnmlOntologyRegistry.findMatchingParameter(funcResource, paramRes)
                    : null;
            if (declared == null) {
                declared = FnmlOntologyRegistry.findParameter(paramRes);
            }
            return declared != null ? declared.name() : RmlEvaluationResultFormatter.compactIri(iri.uri(), prefixMap);
        }
        return format(in.parameterMap.expression, prefixMap);
    }

    private static String formatConstant(RDFNodeConstant c, @Nullable Map<String, String> prefixMap) {
        if (c.constant == null) {
            return "";
        }
        return RmlEvaluationResultFormatter.formatCompactTerm(c.constant, prefixMap, "");
    }

    private static String formatReference(Reference ref) {
        String raw = ref.getReference();
        return raw != null ? raw : "";
    }

    private static String formatTemplate(Template tpl) {
        String raw = tpl.getTemplate();
        return raw != null ? "\"" + raw + "\"" : "\"\"";
    }
}
