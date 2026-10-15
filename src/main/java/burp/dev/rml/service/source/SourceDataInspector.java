package burp.dev.rml.service.source;

import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.eval.EvaluationContexts;
import burp.ls.CSVIteration;
import burp.ls.JSONSourceRFC;
import burp.ls.XMLSource;
import burp.model.AbstractLogicalSource;
import burp.model.Iteration;
import burp.model.PlanNode;
import burp.model.PlanNodeIdRegistry;
import burp.model.lv.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonElementKt;
import kotlinx.serialization.json.JsonPrimitive;
import org.apache.jena.rdf.model.RDFNode;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Service to inspect Logical Views and Logical Sources and generate windowed tabular preview datasets.
 */
@NullMarked
public class SourceDataInspector {

    public CompletableFuture<SourceDataResult> getSourceData(
            SourceDataParams params,
            MappingWorkspace workspace
    ) {
        try {

            MappingWorkspace.ResolvedNode resolved = workspace.resolveNode(params.structure());
            AbstractLogicalSource src = EvaluationContexts.resolveLogicalSource(resolved.targetNode());

            if (src == null) {
                return CompletableFuture.completedFuture(
                        new SourceDataResult(
                                PlanNodeIdRegistry.getId(resolved.targetNode()),
                                PlanNode.resourceToIriOrBNode(resolved.targetNode().getIri()),
                                "",
                                "table",
                                null,
                                null,
                                params.offset(),
                                params.limit(),
                                0,
                                true,
                                false,
                                List.of("Selected structure is not a Logical Source")
                        )
                );
            }

            String sourceType = src.getClass().getSimpleName();
            String format;
            if (src instanceof JSONSourceRFC) {
                format = "json";
            } else if (src instanceof XMLSource) {
                format = "xml";
            } else {
                format = "table";
            }

            Iterable<Iteration> iterator = src.iterator();
            List<Iteration> allIterations = new ArrayList<>();
            for (Iteration it : iterator) {
                allIterations.add(it);
            }

            int offset = Math.max(0, params.offset());
            int limit = params.limit() > 0 ? params.limit() : 50;
            int totalItems = allIterations.size();
            boolean totalExact = true;
            int endIndex = Math.min(offset + limit, totalItems);

            TableData tableData = null;
            DocumentData docData = null;

            if ("json".equals(format)) {
                List<String> items = new ArrayList<>();
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                for (int i = offset; i < endIndex; i++) {
                    Iteration li = allIterations.get(i);
                    String raw = li.asString();
                    try {
                        var el = com.google.gson.JsonParser.parseString(raw);
                        items.add(gson.toJson(el));
                    } catch (Exception e) {
                        items.add(raw);
                    }
                }
                docData = new DocumentData("json", items);
            } else if ("xml".equals(format)) {
                List<String> items = new ArrayList<>();
                for (int i = offset; i < endIndex; i++) {
                    Iteration li = allIterations.get(i);
                    items.add(li.asString());
                }
                docData = new DocumentData("xml", items);
            } else {
                LinkedHashSet<String> headerSet = new LinkedHashSet<>();
                if (src instanceof LogicalView lv) {
                    collectDeclaredHeaders(lv, headerSet);
                }
                for (Iteration it : allIterations) {
                    if (it instanceof LogicalIteration li) {
                        headerSet.addAll(li.map.keySet());
                    } else if (it instanceof CSVIteration ci) {
                        headerSet.addAll(ci.map.keySet());
                    }
                }
                List<String> headers = new ArrayList<>(headerSet);

                List<List<@Nullable String>> rows = new ArrayList<>();
                List<List<@Nullable String>> cellTypes = new ArrayList<>();
                Map<String, String> columnTypes = new LinkedHashMap<>();

                for (int i = offset; i < endIndex; i++) {
                    Iteration li = allIterations.get(i);
                    List<@Nullable String> row = new ArrayList<>(headers.size());
                    List<@Nullable String> types = new ArrayList<>(headers.size());

                    for (String header : headers) {
                        List<Object> vals = extractValuesSafe(src, li, header);
                        Object val = vals.isEmpty() ? null : (vals.size() == 1 ? vals.getFirst() : vals);
                        String strVal = null;
                        String typeVal = null;

                        switch (val) {
                            case null -> {
                            }
                            case Iteration iter -> {
                                strVal = iter.asString();
                                typeVal = "string";
                            }
                            case RDFNode n -> {
                                if (n.isLiteral()) {
                                    strVal = n.asLiteral().getLexicalForm();
                                    typeVal = n.asLiteral().getDatatypeURI();
                                    if (typeVal == null) typeVal = "string";
                                    else if (typeVal.startsWith("http://www.w3.org/2001/XMLSchema#")) {
                                        typeVal = "xsd:" + typeVal.substring("http://www.w3.org/2001/XMLSchema#".length());
                                    }
                                } else {
                                    strVal = n.toString();
                                    typeVal = n.isURIResource() ? "iri" : "bnode";
                                }
                            }
                            case JsonElement je -> {
                                if (je instanceof JsonPrimitive jp) {
                                    if (jp.isString()) {
                                        strVal = jp.getContent();
                                        typeVal = "string";
                                    } else if (JsonElementKt.getBooleanOrNull(jp) != null) {
                                        strVal = String.valueOf(JsonElementKt.getBoolean(jp));
                                        typeVal = "boolean";
                                    } else if (JsonElementKt.getIntOrNull(jp) != null || JsonElementKt.getLongOrNull(jp) != null) {
                                        strVal = jp.getContent();
                                        typeVal = "integer";
                                    } else if (JsonElementKt.getFloatOrNull(jp) != null || JsonElementKt.getDoubleOrNull(jp) != null) {
                                        strVal = jp.getContent();
                                        typeVal = "decimal";
                                    } else {
                                        strVal = jp.getContent();
                                        typeVal = "string";
                                    }
                                } else {
                                    strVal = je.toString();
                                    typeVal = "json";
                                }
                            }
                            default -> {
                                strVal = String.valueOf(val);
                                if (val instanceof Integer || val instanceof Long || val instanceof Short || val instanceof Byte) {
                                    typeVal = "integer";
                                } else if (val instanceof Float || val instanceof Double || val instanceof java.math.BigDecimal) {
                                    typeVal = "decimal";
                                } else if (val instanceof Boolean) {
                                    typeVal = "boolean";
                                } else {
                                    typeVal = "string";
                                }
                            }
                        }

                        row.add(strVal);
                        types.add(typeVal);

                        if (typeVal != null && !columnTypes.containsKey(header)) {
                            columnTypes.put(header, typeVal);
                        }
                    }
                    rows.add(row);
                    cellTypes.add(types);
                }

                for (String header : headers) {
                    columnTypes.putIfAbsent(header, "string");
                }
                tableData = new TableData(headers, columnTypes, cellTypes, rows);
            }

            int loadedCount = tableData != null ? tableData.rows().size() : (docData != null ? docData.items().size() : 0);
            boolean hasMore = (offset + loadedCount) < totalItems;

            return CompletableFuture.completedFuture(
                    new SourceDataResult(
                            PlanNodeIdRegistry.getId(src),
                            PlanNode.resourceToIriOrBNode(src.getIri()),
                            sourceType,
                            format,
                            tableData,
                            docData,
                            offset,
                            limit,
                            totalItems,
                            totalExact,
                            hasMore,
                            Collections.emptyList()
                    )
            );
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    new SourceDataResult(
                            "", null, "",
                            "table",
                            null,
                            null,
                            params.offset(), params.limit(), 0, true, false,
                            List.of("Error evaluating logical source preview: " + e.getMessage())
                    )
            );
        }
    }

    private void collectDeclaredHeaders(LogicalView lv, Set<String> headers) {
        for (Field f : lv.fields) {
            addFieldHeaders(f, headers);
        }
        for (ViewJoin join : lv.joins) {
            for (Field f : join.fields) {
                addFieldHeaders(f, headers);
            }
        }
    }

    private void addFieldHeaders(Field f, Set<String> headers) {
        if (f.fieldName != null) {
            String name = f.getAbsoluteFieldName();
            headers.add(name != null ? name : f.fieldName);
        }
        if (f instanceof IterableField inf) {
            for (Field child : inf.fields) {
                addFieldHeaders(child, headers);
            }
        }
    }

    private List<Object> extractValuesSafe(AbstractLogicalSource src, Iteration li, String header) {
        try {
            return src.extractValues(li, header);
        } catch (Exception e) {
            if (li instanceof LogicalIteration logIt && logIt.map.containsKey(header)) {
                Object raw = logIt.map.get(header);
                return raw == null ? Collections.emptyList() : Collections.singletonList(raw);
            }
            return Collections.emptyList();
        }
    }
}
