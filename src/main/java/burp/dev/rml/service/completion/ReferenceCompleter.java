package burp.dev.rml.service.completion;

import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.RmlEvaluationResultFormatter;
import burp.dev.rml.service.plan.PlanNodeDescriptor;
import burp.dev.rml.service.selector.CursorSelector;
import burp.model.*;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Handles standard LSP textDocument/completion requests across:
 * - FnO Function Parameters (rml:parameter / Input / ParameterMap)
 * - FnO Functions (rml:function / FunctionMap)
 * - Logical source references (CSV, CSVW, JSON, JSON Schemas, LogicalViews, and Joins)
 */
@NullMarked
public class ReferenceCompleter {

    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> complete(
            @Nullable CompletionParams params,
            MappingWorkspace workspace
    ) {
        if (params == null || params.getTextDocument() == null || params.getPosition() == null) {
            return CompletableFuture.completedFuture(Either.forRight(new CompletionList(false, Collections.emptyList())));
        }

        try {
            String uri = params.getTextDocument().getUri();
            Position pos = params.getPosition();
            CursorSelector cs = new CursorSelector(uri, pos);

            // 1. Extract prefix and replacement range from document text at cursor
            String openDoc = workspace.getOpenDocumentText(uri);
            if (openDoc == null) {
                Path mappingPath = workspace.normalizePath(uri);
                if (!mappingPath.isAbsolute()) {
                    mappingPath = workspace.getCurrentWorkingDirectory().resolve(mappingPath).normalize();
                }
                if (Files.exists(mappingPath)) {
                    openDoc = Files.readString(mappingPath);
                }
            }

            String effectivePrefix = "";
            Range replaceRange = null;
            if (openDoc != null) {
                String[] lines = openDoc.split("\r?\n");
                if (pos.getLine() >= 0 && pos.getLine() < lines.length) {
                    String lineText = lines[pos.getLine()];
                    int charPos = Math.min(pos.getCharacter(), lineText.length());
                    int start = charPos;
                    while (start > 0) {
                        char ch = lineText.charAt(start - 1);
                        if (Character.isWhitespace(ch) || ch == '"' || ch == '{' || ch == '(' || ch == ';' || ch == ',' || ch == '[' || ch == ']') {
                            break;
                        }
                        start--;
                    }
                    effectivePrefix = lineText.substring(start, charPos);
                    int end = charPos;
                    while (end < lineText.length()) {
                        char ch = lineText.charAt(end);
                        if (Character.isWhitespace(ch) || ch == '"' || ch == '}' || ch == ')' || ch == ';' || ch == ',' || ch == '[' || ch == ']') {
                            break;
                        }
                        end++;
                    }
                    replaceRange = new Range(new Position(pos.getLine(), start), new Position(pos.getLine(), end));
                }
            }

            MappingWorkspace.ResolvedNode resolved = workspace.resolveNode(cs);
            PlanNode targetNode = resolved.targetNode();
            if (replaceRange == null) {
                replaceRange = PlanNodeDescriptor.getFirstRangeForNode(targetNode);
            }

            MappingWorkspace.LoadedMapping loaded = workspace.loadMappingDetailed(uri);
            Map<String, String> prefixMap = new HashMap<>(loaded.document().prefixMap);
            if (loaded.model() != null) {
                prefixMap.putAll(loaded.model().getNsPrefixMap());
            }

            FunctionExecution fe = findEnclosingFunctionExecution(targetNode, cs, loaded);

            List<CompletionItem> items;
            // 1. Check if context is a Function Parameter
            if (isParameterContext(targetNode, fe, cs, workspace)) {
                items = completeParameters(fe, prefixMap, effectivePrefix, replaceRange, targetNode, cs);
            }
            // 2. Check if context is a Function
            else if (isFunctionContext(targetNode, fe, cs, workspace)) {
                items = completeFunctions(prefixMap, effectivePrefix, replaceRange, targetNode);
            }
            // 3. Check if context is a Logical Source Reference
            else if (isReferenceContext(targetNode)) {
                items = completeLogicalSourceReferences(targetNode, effectivePrefix, replaceRange, workspace.getIterationSampleLimit());
            } else {
                items = Collections.emptyList();
            }

            return CompletableFuture.completedFuture(Either.forRight(new CompletionList(false, items)));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(Either.forRight(new CompletionList(false, Collections.emptyList())));
        }
    }

    private @Nullable FunctionExecution findEnclosingFunctionExecution(
            PlanNode targetNode,
            @Nullable CursorSelector cs,
            MappingWorkspace.@Nullable LoadedMapping loaded
    ) {
        FunctionExecution fe = targetNode instanceof FunctionExecution f ? f : targetNode.ancestor(FunctionExecution.class);
        if (fe != null) return fe;

        if (loaded != null) {
            for (TriplesMap t : loaded.document().triplesMaps) {
                for (FunctionExecution candidate : t.descendants(FunctionExecution.class).toList()) {
                    if (candidate.descendants(PlanNode.class).anyMatch(n -> n == targetNode)) {
                        return candidate;
                    }
                    if (cs != null) {
                        Range r = PlanNodeDescriptor.getFirstRangeForNode(candidate);
                        if (r != null && isPositionInsideOrAt(r, cs.position())) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isPositionInsideOrAt(Range range, Position pos) {
        if (range.getStart() == null || range.getEnd() == null) return false;
        int startLine = range.getStart().getLine();
        int endLine = range.getEnd().getLine();
        int line = pos.getLine();
        if (line < startLine || line > endLine) return false;
        if (line == startLine && pos.getCharacter() < range.getStart().getCharacter()) return false;
        if (line == endLine && pos.getCharacter() > range.getEnd().getCharacter()) return false;
        return true;
    }

    private boolean isParameterContext(
            PlanNode targetNode,
            @Nullable FunctionExecution fe,
            @Nullable CursorSelector cs,
            MappingWorkspace workspace
    ) {
        if (cs != null) {
            String openDoc = workspace.getOpenDocumentText(cs.mappingFileUri());
            if (openDoc != null) {
                int line = cs.position().getLine();
                String[] lines = openDoc.split("\r?\n");
                if (line >= 0 && line < lines.length) {
                    String lineText = lines[line];
                    if (lineText.contains("function") || lineText.contains("rml:function")) {
                        return false;
                    }
                    if (lineText.contains("parameter") || lineText.contains("rml:parameter")) {
                        return true;
                    }
                }
            }
        }
        if (targetNode instanceof ParameterMap || targetNode.ancestor(ParameterMap.class) != null) {
            return true;
        }
        if (targetNode instanceof Input) {
            return true;
        }
        return false;
    }

    private boolean isFunctionContext(
            PlanNode targetNode,
            @Nullable FunctionExecution fe,
            @Nullable CursorSelector cs,
            MappingWorkspace workspace
    ) {
        if (cs != null) {
            String openDoc = workspace.getOpenDocumentText(cs.mappingFileUri());
            if (openDoc != null) {
                int line = cs.position().getLine();
                String[] lines = openDoc.split("\r?\n");
                if (line >= 0 && line < lines.length) {
                    String lineText = lines[line];
                    if (lineText.contains("parameter") || lineText.contains("rml:parameter")) {
                        return false;
                    }
                    if (lineText.contains("function") || lineText.contains("rml:function")) {
                        return true;
                    }
                }
            }
        }
        if (targetNode instanceof FunctionMap || targetNode.ancestor(FunctionMap.class) != null) {
            return true;
        }
        return false;
    }

    private boolean isReferenceContext(PlanNode targetNode) {
        return targetNode instanceof RawReference
                || targetNode.ancestor(RawReference.class) != null
                || targetNode instanceof Reference
                || targetNode.ancestor(Reference.class) != null
                || targetNode instanceof ExpressionMap;
    }

    private List<CompletionItem> completeParameters(
            @Nullable FunctionExecution fe,
            Map<String, String> prefixMap,
            String effectivePrefix,
            @Nullable Range targetRange,
            PlanNode targetNode,
            @Nullable CursorSelector cs
    ) {
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
        for (Input input : fe.inputs) {
            boolean isCurrentInput = (targetNode == input || isDescendantOrSelf(targetNode, input));

            if (!isCurrentInput && input.parameterMap != null && input.parameterMap.expression instanceof RDFNodeConstant c) {
                if (c.constant instanceof IRITerm iriTerm) {
                    usedParamUris.add(iriTerm.uri());
                }
            }
        }

        String pointer = targetNode.getSourcePointers().stream()
                .map(PlanNodeDescriptor::getRdfPointerString)
                .findFirst()
                .orElse("FnO Parameter for " + funcResource.getURI());

        for (FnmlParameter param : expectedParams) {
            String paramUri = param.iri().getURI();
            String predicateUri = param.predicateIri() != null ? param.predicateIri().getURI() : null;

            if (usedParamUris.contains(paramUri)
                    || (predicateUri != null && usedParamUris.contains(predicateUri))) {
                continue;
            }

            String paramCompact = RmlEvaluationResultFormatter.compactIri(paramUri, prefixMap);
            String predicateCompact = predicateUri != null
                    ? RmlEvaluationResultFormatter.compactIri(predicateUri, prefixMap)
                    : null;

            String typeDisplay = param.typeIri() != null
                    ? RmlEvaluationResultFormatter.compactIri(param.typeIri().getURI(), prefixMap)
                    : "fno:Parameter";
            String status = param.required() ? " [required]" : " [optional]";
            String exampleVal = param.name() + status;

            // 1. Primary: offer parameter definition IRI (e.g. grel:valueParam, idlab-fn:_str)
            if (matchesPrefix(paramCompact, param.iri().getURI(), param.iri().getLocalName(), effectivePrefix)) {
                if (seenCompletions.add(paramCompact)) {
                    CompletionItem item = new CompletionItem(paramCompact);
                    item.setKind(CompletionItemKind.Property);
                    item.setDetail("[" + typeDisplay + "] example: " + exampleVal);
                    item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, "**FnO Parameter** for `" + funcResource.getLocalName() + "`\n\n**Origin Graph Pointer**:\n`" + pointer + "`"));
                    item.setInsertText(paramCompact);
                    if (targetRange != null) {
                        item.setTextEdit(Either.forLeft(new TextEdit(targetRange, paramCompact)));
                    }
                    results.add(item);
                }
            }

            // 2. Secondary: offer predicate IRI if distinct (e.g. grel:valueParameter or idlab-fn:str)
            if (predicateCompact != null && !predicateCompact.equals(paramCompact)) {
                if (matchesPrefix(predicateCompact, param.predicateIri().getURI(), param.predicateIri().getLocalName(), effectivePrefix)) {
                    if (seenCompletions.add(predicateCompact)) {
                        CompletionItem item = new CompletionItem(predicateCompact);
                        item.setKind(CompletionItemKind.Property);
                        item.setDetail("[" + typeDisplay + "] (predicate) example: " + exampleVal);
                        item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, "**FnO Predicate** for `" + funcResource.getLocalName() + "`\n\n**Origin Graph Pointer**:\n`" + pointer + "`"));
                        item.setInsertText(predicateCompact);
                        if (targetRange != null) {
                            item.setTextEdit(Either.forLeft(new TextEdit(targetRange, predicateCompact)));
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

    private List<CompletionItem> completeFunctions(
            Map<String, String> prefixMap,
            String effectivePrefix,
            @Nullable Range targetRange,
            PlanNode targetNode
    ) {
        List<CompletionItem> results = new ArrayList<>();
        String pointer = targetNode.getSourcePointers().stream()
                .map(PlanNodeDescriptor::getRdfPointerString)
                .findFirst()
                .orElse("FnO Function");

        for (Resource func : FnmlOntologyRegistry.getDeclaredFunctions()) {
            String compactFunc = RmlEvaluationResultFormatter.compactIri(func.getURI(), prefixMap);
            if (matchesPrefix(compactFunc, func.getURI(), func.getLocalName(), effectivePrefix)) {
                List<FnmlParameter> params = FnmlOntologyRegistry.getParameters(func);
                String paramsSummary = params.stream().map(FnmlParameter::name).collect(Collectors.joining(", "));

                CompletionItem item = new CompletionItem(compactFunc);
                item.setKind(CompletionItemKind.Function);
                item.setDetail("fno:Function" + (!paramsSummary.isEmpty() ? " (" + paramsSummary + ")" : ""));
                item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, "**FnO Function Definition**:\n`" + func.getURI() + "`\n\n**Origin Graph Pointer**:\n`" + pointer + "`"));
                item.setInsertText(compactFunc);
                if (targetRange != null) {
                    item.setTextEdit(Either.forLeft(new TextEdit(targetRange, compactFunc)));
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

    private List<CompletionItem> completeLogicalSourceReferences(
            PlanNode targetNode,
            String effectivePrefix,
            @Nullable Range targetRange,
            @Nullable Integer iterationSampleLimit
    ) {
        AbstractLogicalSource ls = ReferenceSchemaResolver.resolveContextualLogicalSource(targetNode);
        if (ls == null) return Collections.emptyList(); // Without logical source we cannot complete references
        ReferenceSchemaResolver.SchemaInfo schemaInfo = ReferenceSchemaResolver.extractSchemaInfo(targetNode, ls, iterationSampleLimit);

        List<CompletionItem> results = new ArrayList<>();
        for (var entry : schemaInfo.candidateValues().entrySet()) {
            String fullRef = entry.getKey();
            var exampleVal = entry.getValue();

            boolean matches = effectivePrefix.isEmpty()
                    || fullRef.startsWith(effectivePrefix)
                    || fullRef.toLowerCase().contains(effectivePrefix.toLowerCase())
                    || (fullRef.startsWith("$.") && fullRef.substring(2).startsWith(effectivePrefix));

            if (matches) {
                String shortFormulation = schemaInfo.referenceFormulation().replace("http://w3id.org/rml/", "");
                String detail = "[" + shortFormulation + "]";

                if (!exampleVal.isEmpty()) detail += "\n\nexample: " + exampleVal;

                var origin = schemaInfo.candidateOrigins().get(fullRef);
                if (origin != null) {
                    detail += "\n\nfrom: " + origin.type();
                }

                CompletionItem item = new CompletionItem(fullRef);
                item.setKind(CompletionItemKind.Field);
                item.setDetail(detail);
                item.setInsertText(fullRef);
                if (targetRange != null) {
                    item.setTextEdit(Either.forLeft(new TextEdit(targetRange, fullRef)));
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

