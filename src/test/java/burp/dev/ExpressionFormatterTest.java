package burp.dev;

import burp.dev.rml.service.RmlEvaluationResultFormatter;
import burp.dev.rml.service.RmlEvaluationResultFormatter;
import burp.dev.rml.service.hint.ExpressionFormatter;
import burp.model.*;
import burp.model.fnml.FunctionExecution;
import burp.model.rdf.IRITerm;
import burp.model.rdf.LiteralTerm;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ExpressionFormatterTest {

    private final Map<String, String> prefixMap = Map.of(
            "ex", "http://example.com/",
            "idlab-fn", "https://w3id.org/imec/idlab/function#",
            "xsd", "http://www.w3.org/2001/XMLSchema#"
    );

    @Test
    public void testFormatConstant() {
        RDFNodeConstant strConst = new RDFNodeConstant(new LiteralTerm("hello"));
        assertThat(ExpressionFormatter.format(strConst, prefixMap)).isEqualTo("\"hello\"");

        RDFNodeConstant boolConst = new RDFNodeConstant(new LiteralTerm("true", new IRITerm("http://www.w3.org/2001/XMLSchema#boolean")));
        assertThat(ExpressionFormatter.format(boolConst, prefixMap)).isEqualTo("true");

        LiteralTerm intLit = new LiteralTerm("42", new IRITerm("http://www.w3.org/2001/XMLSchema#integer"));
        assertThat(RmlEvaluationResultFormatter.formatTerm(intLit, prefixMap, "")).isEqualTo("\"42\"^^xsd:integer");
        assertThat(RmlEvaluationResultFormatter.formatCompactTerm(intLit, prefixMap, "")).isEqualTo("42");
        assertThat(ExpressionFormatter.format(new RDFNodeConstant(intLit), prefixMap)).isEqualTo("42");

        RDFNodeConstant iriConst = new RDFNodeConstant(new IRITerm("http://example.com/item"));
        assertThat(ExpressionFormatter.format(iriConst, prefixMap)).isEqualTo("ex:item");
    }

    @Test
    public void testFormatReferenceAndTemplate() {
        Reference ref = new RawReference("deptId");
        assertThat(ExpressionFormatter.format(ref, prefixMap)).isEqualTo("deptId");

        Template tpl = new Template("http://example.com/users/{id}");
        assertThat(ExpressionFormatter.format(tpl, prefixMap)).isEqualTo("\"http://example.com/users/{id}\"");
    }

    @Test
    public void testFormatFunctionExecution() {
        FunctionExecution fe = new FunctionExecution();
        fe.functionMap = new FunctionMap();
        fe.functionMap.expression = new RDFNodeConstant(new IRITerm("https://w3id.org/imec/idlab/function#concat"));

        Input in1 = new Input();
        in1.parameterMap = new ParameterMap();
        in1.parameterMap.expression = new RDFNodeConstant(new IRITerm("https://w3id.org/imec/idlab/function#str"));
        in1.inputValueMap = new InputValueMap();
        in1.inputValueMap.expression = new RawReference("Name");

        Input in2 = new Input();
        in2.parameterMap = new ParameterMap();
        in2.parameterMap.expression = new RDFNodeConstant(new IRITerm("https://w3id.org/imec/idlab/function#delimiter"));
        in2.inputValueMap = new InputValueMap();
        in2.inputValueMap.expression = new RDFNodeConstant(new LiteralTerm(" - "));

        fe.inputs.addAll(List.of(in1, in2));

        String formatted = ExpressionFormatter.formatFunctionExecution(fe, prefixMap);
        assertThat(formatted).isEqualTo("concat(input string = Name, delimiter = \" - \")");
    }

    @Test
    public void testFormatNestedFunctionExecution() {
        FunctionExecution inner = new FunctionExecution();
        inner.functionMap = new FunctionMap();
        inner.functionMap.expression = new RDFNodeConstant(new IRITerm("https://w3id.org/imec/idlab/function#concat"));

        Input inInner = new Input();
        inInner.parameterMap = new ParameterMap();
        inInner.parameterMap.expression = new RDFNodeConstant(new IRITerm("https://w3id.org/imec/idlab/function#str"));
        inInner.inputValueMap = new InputValueMap();
        inInner.inputValueMap.expression = new RawReference("Name");
        inner.inputs.add(inInner);

        FunctionExecution outer = new FunctionExecution();
        outer.functionMap = new FunctionMap();
        outer.functionMap.expression = new RDFNodeConstant(new IRITerm("http://example.com/customTransform"));

        Input inOuter = new Input();
        inOuter.parameterMap = new ParameterMap();
        inOuter.parameterMap.expression = new RDFNodeConstant(new IRITerm("http://example.com/inputParam"));
        inOuter.inputValueMap = new InputValueMap();
        inOuter.inputValueMap.expression = inner;
        outer.inputs.add(inOuter);

        String formatted = ExpressionFormatter.formatFunctionExecution(outer, prefixMap);
        assertThat(formatted).isEqualTo("ex:customTransform(ex:inputParam = concat(input string = Name))");
    }

    @Test
    public void testFormatFunctionSubstringWithNumbersAndOrder() {
        FunctionExecution fe = new FunctionExecution();
        fe.functionMap = new FunctionMap();
        fe.functionMap.expression = new RDFNodeConstant(new IRITerm("http://users.ugent.be/~bjdmeest/function/grel.ttl#string_substring"));

        // Add inputs deliberately out of declared order: "to" first, then "input value", then "from"
        Input inTo = new Input();
        inTo.parameterMap = new ParameterMap();
        inTo.parameterMap.expression = new RDFNodeConstant(new IRITerm("http://users.ugent.be/~bjdmeest/function/grel.ttl#p_int_i_opt_to"));
        inTo.inputValueMap = new InputValueMap();
        inTo.inputValueMap.expression = new RDFNodeConstant(new LiteralTerm("3", new IRITerm("http://www.w3.org/2001/XMLSchema#integer")));

        Input inVal = new Input();
        inVal.parameterMap = new ParameterMap();
        inVal.parameterMap.expression = new RDFNodeConstant(new IRITerm("http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam"));
        inVal.inputValueMap = new InputValueMap();
        inVal.inputValueMap.expression = new RawReference("title");

        Input inFrom = new Input();
        inFrom.parameterMap = new ParameterMap();
        inFrom.parameterMap.expression = new RDFNodeConstant(new IRITerm("http://users.ugent.be/~bjdmeest/function/grel.ttl#p_int_i_from"));
        inFrom.inputValueMap = new InputValueMap();
        inFrom.inputValueMap.expression = new RDFNodeConstant(new LiteralTerm("0", new IRITerm("http://www.w3.org/2001/XMLSchema#int")));

        fe.inputs.addAll(List.of(inTo, inVal, inFrom));

        String formatted = ExpressionFormatter.formatFunctionExecution(fe, prefixMap);
        assertThat(formatted).isEqualTo("substring(input value = title, from = 0, to = 3)");
    }

    @Test
    public void testFormatJoinCondition() {
        JoinCondition jc = new JoinCondition();
        jc.childMap = new ChildMap();
        jc.childMap.expression = new RawReference("deptId");
        jc.parentMap = new ParentMap();
        jc.parentMap.expression = new RawReference("id");

        String formatted = ExpressionFormatter.formatJoinCondition(jc, prefixMap);
        assertThat(formatted).isEqualTo("deptId == id");
    }
}
