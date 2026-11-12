package burp.dev.rml.service.completion;

import burp.dev.rml.service.RmlEvaluationResultFormatter;
import burp.dev.rml.service.plan.PlanNodeDescriptor;
import burp.model.FunctionMap;
import burp.model.Input;
import burp.model.PlanNode;
import burp.model.RDFNodeConstant;
import burp.model.fnml.FnmlOntologyRegistry;
import burp.model.fnml.FnmlParameter;
import burp.model.fnml.FunctionExecution;
import burp.model.rdf.IRITerm;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/// Provides autocompletion for FnO (Function Ontology) functions and execution parameters.
@NullMarked
public class FunctionCompleter implements CompletionContributor {

    @Override
    public boolean isApplicable(CompletionContext ctx) {
        if (ctx.hasAncestor(FunctionMap.class)) return true;
        String clause = ctx.currentClauseText();
        return clause.contains("function") || clause.contains("executes");
    }

    @Override
    public List<CompletionItem> complete(CompletionContext ctx) {
        return completeFunctions(ctx);
    }

    @Override
    public int priority() {
        return 40;
    }

    /// Autocompletes function execution parameters (`rml:parameter`).
    public List<CompletionItem> completeParameters(CompletionContext ctx) {
        FunctionExecution fe = ctx.findEnclosingFunctionExecution();
        if (fe == null) {
            return Collections.emptyList();
        }

        Resource funcResource = null;
        if (fe.functionMap != null && fe.functionMap.expression instanceof RDFNodeConstant c) {
            if (c.constant instanceof IRITerm iriTerm) {
                funcResource = ResourceFactory.createResource(iriTerm.uri());
            }
        }
        if (funcResource == null) {
            return Collections.emptyList();
        }

        List<FnmlParameter> expectedParams = FnmlOntologyRegistry.getParameters(funcResource);
        List<CompletionItem> results = new ArrayList<>();
        Set<String> seenCompletions = new HashSet<>();

        // Collect parameters already used in other inputs of this function execution
        Set<String> usedParamUris = new HashSet<>();
        PlanNode targetNode = ctx.targetNode();
        for (Input input : fe.inputs) {
            boolean isCurrentInput = (targetNode == input || isDescendantOrSelf(targetNode, input));

            if (!isCurrentInput && input.parameterMap != null && input.parameterMap.expression instanceof RDFNodeConstant c) {
                if (c.constant instanceof IRITerm iriTerm) {
                    usedParamUris.add(iriTerm.uri());
                }
            }
        }

        String pointer = targetNode != null ? targetNode.getSourcePointers().stream()
                .map(PlanNodeDescriptor::getRdfPointerString)
                .findFirst()
                .orElse("FnO Parameter for " + funcResource.getURI()) : "FnO Parameter for " + funcResource.getURI();

        for (FnmlParameter param : expectedParams) {
            String paramUri = param.iri().getURI();
            String predicateUri = param.predicateIri() != null ? param.predicateIri().getURI() : null;

            if (usedParamUris.contains(paramUri)
                    || (predicateUri != null && usedParamUris.contains(predicateUri))) {
                continue;
            }

            String paramCompact = RmlEvaluationResultFormatter.compactIri(paramUri, ctx.prefixMap());
            String predicateCompact = predicateUri != null
                    ? RmlEvaluationResultFormatter.compactIri(predicateUri, ctx.prefixMap())
                    : null;

            String typeDisplay = param.typeIri() != null
                    ? RmlEvaluationResultFormatter.compactIri(param.typeIri().getURI(), ctx.prefixMap())
                    : "fno:Parameter";
            String status = param.required() ? " [required]" : " [optional]";
            String exampleVal = param.name() + status;

            // 1. Primary: offer parameter definition IRI (e.g. grel:valueParam, idlab-fn:_str)
            if (matchesPrefix(paramCompact, param.iri().getURI(), param.iri().getLocalName(), ctx.effectivePrefix())) {
                if (seenCompletions.add(paramCompact)) {
                    CompletionItem item = new CompletionItem(paramCompact);
                    item.setKind(CompletionItemKind.Property);
                    item.setDetail("[" + typeDisplay + "] example: " + exampleVal);
                    item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, "**FnO Parameter** for `" + funcResource.getLocalName() + "`\n\n**Origin Graph Pointer**:\n`" + pointer + "`"));
                    item.setInsertText(paramCompact);
                    if (ctx.replaceRange() != null) {
                        item.setTextEdit(Either.forLeft(new TextEdit(ctx.replaceRange(), paramCompact)));
                    }
                    results.add(item);
                }
            }

            // 2. Secondary: offer predicate IRI if distinct (e.g. grel:valueParameter or idlab-fn:str)
            if (predicateCompact != null && !predicateCompact.equals(paramCompact)) {
                if (matchesPrefix(predicateCompact, param.predicateIri().getURI(), param.predicateIri().getLocalName(), ctx.effectivePrefix())) {
                    if (seenCompletions.add(predicateCompact)) {
                        CompletionItem item = new CompletionItem(predicateCompact);
                        item.setKind(CompletionItemKind.Property);
                        item.setDetail("[" + typeDisplay + "] (predicate) example: " + exampleVal);
                        item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, "**FnO Predicate** for `" + funcResource.getLocalName() + "`\n\n**Origin Graph Pointer**:\n`" + pointer + "`"));
                        item.setInsertText(predicateCompact);
                        if (ctx.replaceRange() != null) {
                            item.setTextEdit(Either.forLeft(new TextEdit(ctx.replaceRange(), predicateCompact)));
                        }
                        results.add(item);
                    }
                }
            }
        }

        // Sort required parameters first
        results.sort(Comparator.comparing((CompletionItem c) ->
                c.getDetail() != null && c.getDetail().contains("[required]") ? 0 : 1
        ));

        for (int i = 0; i < results.size(); i++) {
            results.get(i).setSortText(String.format("%04d", i));
        }

        return results;
    }

    /// Autocompletes function definitions (`rml:function` or `fno:executes`).
    public List<CompletionItem> completeFunctions(CompletionContext ctx) {
        List<CompletionItem> results = new ArrayList<>();
        PlanNode targetNode = ctx.targetNode();
        String pointer = targetNode != null ? targetNode.getSourcePointers().stream()
                .map(PlanNodeDescriptor::getRdfPointerString)
                .findFirst()
                .orElse("FnO Function") : "FnO Function";

        for (Resource func : FnmlOntologyRegistry.getDeclaredFunctions()) {
            String compactFunc = RmlEvaluationResultFormatter.compactIri(func.getURI(), ctx.prefixMap());
            if (matchesPrefix(compactFunc, func.getURI(), func.getLocalName(), ctx.effectivePrefix())) {
                List<FnmlParameter> params = FnmlOntologyRegistry.getParameters(func);
                String paramsSummary = params.stream().map(FnmlParameter::name).collect(Collectors.joining(", "));

                CompletionItem item = new CompletionItem(compactFunc);
                item.setKind(CompletionItemKind.Function);
                item.setDetail("fno:Function" + (!paramsSummary.isEmpty() ? " (" + paramsSummary + ")" : ""));
                item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, "**FnO Function Definition**:\n`" + func.getURI() + "`\n\n**Origin Graph Pointer**:\n`" + pointer + "`"));
                item.setInsertText(compactFunc);
                if (ctx.replaceRange() != null) {
                    item.setTextEdit(Either.forLeft(new TextEdit(ctx.replaceRange(), compactFunc)));
                }
                results.add(item);
            }
        }

        results.sort(Comparator.comparing(CompletionItem::getLabel));
        for (int i = 0; i < results.size(); i++) {
            results.get(i).setSortText(String.format("%04d", i));
        }
        return results;
    }

    private boolean matchesPrefix(String compactIri, String fullIri, String localName, String effectivePrefix) {
        if (effectivePrefix.isEmpty()) return true;
        String lower = effectivePrefix.toLowerCase();
        return compactIri.toLowerCase().startsWith(lower)
                || compactIri.toLowerCase().contains(lower)
                || localName.toLowerCase().startsWith(lower)
                || fullIri.toLowerCase().startsWith(lower);
    }

    private boolean isDescendantOrSelf(@Nullable PlanNode node, @Nullable PlanNode root) {
        if (node == null || root == null) return false;
        if (node == root) return true;
        PlanNode current = node;
        while (current != null) {
            if (current == root) return true;
            current = current.getParent();
        }
        return false;
    }
}
