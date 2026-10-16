package burp.dev.rml.service.hint;

import burp.dev.rml.service.plan.PlanNodeDescriptor;
import burp.model.Input;
import burp.model.PlanNode;
import burp.model.PlanNodeIdRegistry;
import burp.model.RDFNodeConstant;
import burp.model.fnml.FnmlOntologyRegistry;
import burp.model.fnml.FunctionExecution;
import burp.model.rdf.IRITerm;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service to statically extract parameter name and type hints from mapping AST
 * in constant parameter mapping scenarios.
 */
@NullMarked
public class ParameterHintProvider {

    public List<InlayHint> extractInlayHints(
            PlanNode rootNode,
            @Nullable Map<String, String> prefixMap
    ) {
        List<InlayHint> hints = new ArrayList<>();
        rootNode.descendants(FunctionExecution.class).forEach(fe -> collectInlayHints(fe, hints, prefixMap));
        return hints;
    }

    private void collectInlayHints(
            FunctionExecution fe,
            List<InlayHint> hints,
            @Nullable Map<String, String> prefixMap
    ) {
        for (Input in : fe.inputs) {
            if (in.parameterMap != null) {
                var ranges = PlanNodeDescriptor.getRangeForNode(in.parameterMap);
                var range = ranges.isEmpty() ? null : ranges.getLast();
                if (range != null && range.getEnd() != null && in.parameterMap.expression instanceof RDFNodeConstant c) {
                    if (c.constant instanceof IRITerm iriTerm) {
                        Resource paramRes = ResourceFactory.createResource(iriTerm.uri());
                        String hint = FnmlOntologyRegistry.formatParameterHint(paramRes, prefixMap);
                        if (hint != null) {
                            InlayHint inlayHint = createInlayHint(in, range, hint);
                            hints.add(inlayHint);
                        }
                    }
                }
            }
        }
    }

    private static InlayHint createInlayHint(Input in, Range range, String hint) {
        InlayHint inlayHint = new InlayHint();
        inlayHint.setPosition(range.getEnd());
        inlayHint.setLabel(Either.forLeft(": " + hint));
        inlayHint.setKind(InlayHintKind.Parameter);
        inlayHint.setPaddingLeft(true);
        String paramId = in.parameterMap != null ? PlanNodeIdRegistry.getId(in.parameterMap) : PlanNodeIdRegistry.getId(in);
        inlayHint.setTooltip(Either.forRight(new MarkupContent("markdown",
                "**FnO Parameter**: `" + hint + "`\n\n" +
                        "Parameter ID: `" + paramId + "`")));
        return inlayHint;
    }
}

