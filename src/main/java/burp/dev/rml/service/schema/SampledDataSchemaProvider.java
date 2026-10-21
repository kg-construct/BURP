package burp.dev.rml.service.schema;

import burp.dev.rml.service.completion.ReferenceSchemaResolver.SchemaInfo;
import burp.dev.rml.service.eval.EvaluationContexts;
import burp.ls.CSVIteration;
import burp.ls.CSVSource;
import burp.ls.FileBasedLogicalSource;
import burp.ls.JSONSourceRFC;
import burp.model.AbstractLogicalSource;
import burp.model.Iteration;
import burp.model.PlanNode;
import burp.model.lv.IterableField;
import burp.model.lv.LogicalIteration;
import burp.model.lv.LogicalView;
import burp.vocabularies.Rml;
import kotlinx.serialization.json.*;
import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fallback schema provider extracting candidate references and sample values
 * from runtime sample iterations for CSV, JSONPath, and LogicalViews (skips SQL).
 */
@NullMarked
public class SampledDataSchemaProvider implements SchemaProvider {

    private final @Nullable Integer iterationSampleLimit;

    public SampledDataSchemaProvider(@Nullable Integer iterationSampleLimit) {
        this.iterationSampleLimit = iterationSampleLimit;
    }

    public @Nullable Integer getIterationSampleLimit() {
        return iterationSampleLimit;
    }

    @Override
    public boolean supports(@Nullable AbstractLogicalSource logicalSource) {
        if (logicalSource == null) return false;
        return logicalSource instanceof CSVSource
                || logicalSource instanceof JSONSourceRFC
                || logicalSource instanceof LogicalView;
    }

    @Override
    public SchemaInfo extractSchema(AbstractLogicalSource logicalSource) {
        return extractSchema(logicalSource, null);
    }

    public SchemaInfo extractSchema(AbstractLogicalSource logicalSource, @Nullable PlanNode targetNode) {
        Map<String, String> candidateValues = new LinkedHashMap<>();
        Map<String, ReferenceCompletionOrigin> candidateOrigins = new LinkedHashMap<>();
        boolean hasKnownSchemaOrData = false;

        String refFormulation = Rml.JSONPath;
        if (logicalSource instanceof CSVSource) {
            refFormulation = Rml.CSV;
        } else if (logicalSource instanceof LogicalView) {
            refFormulation = Rml.LogicalView;
        }
        if (targetNode != null) {
            IterableField enclosingIter = targetNode.ancestor(IterableField.class);
            if (enclosingIter != null) {
                Resource rf = enclosingIter.getReferenceFormulation();
                if (rf != null && rf.getURI() != null) {
                    refFormulation = rf.getURI();
                }
            }
        }

        try {
            var it = logicalSource.iterator();
            if (it != null) {
                Iterator<Iteration> iterIterator = it.iterator();
                List<Iteration> sampledIters = new ArrayList<>();
                while (iterIterator.hasNext() && (iterationSampleLimit == null || sampledIters.size() < iterationSampleLimit)) {
                    sampledIters.add(iterIterator.next());
                }
                if (targetNode != null) {
                    sampledIters = EvaluationContexts.resolveNodeIterations(targetNode, sampledIters);
                }
                for (Iteration sampleIter : sampledIters) {
                    if (sampleIter instanceof CSVIteration csvIter) {
                        for (var entry : csvIter.map.entrySet()) {
                            candidateValues.putIfAbsent(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
                            candidateOrigins.putIfAbsent(entry.getKey(), new ReferenceCompletionOrigin("data", null, "csv_header", null));
                        }
                    } else if (sampleIter instanceof LogicalIteration li) {
                        for (var entry : li.map.entrySet()) {
                            if (!entry.getKey().startsWith("<it>") && !entry.getKey().equals("#")) {
                                String val = entry.getValue() != null ? entry.getValue().toString() : "";
                                candidateValues.putIfAbsent(entry.getKey(), val);
                                candidateOrigins.putIfAbsent(entry.getKey(), new ReferenceCompletionOrigin("logicalView", null, "sampled_execution", null));
                            }
                        }
                    } else {
                        try {
                            JsonElement jsonElem = Json.Default.parseToJsonElement(sampleIter.asString());
                            String dataFile = logicalSource instanceof FileBasedLogicalSource fbls && fbls.file != null
                                    ? fbls.file.toString()
                                    : "data";
                            collectJsonPaths(jsonElem, "$", dataFile, candidateValues, candidateOrigins);
                        } catch (Exception ignored) {
                        }
                    }
                    hasKnownSchemaOrData = !candidateValues.isEmpty();
                }
            }
        } catch (Exception ignored) {
        }

        return new SchemaInfo(refFormulation, candidateValues, candidateOrigins, hasKnownSchemaOrData);
    }

    private void collectJsonPaths(
            @Nullable JsonElement elem,
            String currentPath,
            String dataFile,
            Map<String, String> paths,
            Map<String, ReferenceCompletionOrigin> origins
    ) {
        switch (elem) {
            case JsonObject obj -> {
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    String key = entry.getKey();
                    String childPath = currentPath.equals("$") ? "$." + key : currentPath + "." + key;

                    String example = null;
                    if (entry.getValue() instanceof JsonPrimitive prim) {
                        example = prim.getContent();
                    }

                    if (example != null && !example.isEmpty()) {
                        paths.putIfAbsent(childPath, example);
                    } else {
                        paths.putIfAbsent(childPath, "");
                    }
                    origins.putIfAbsent(childPath, new ReferenceCompletionOrigin("data", null, dataFile, null));

                    collectJsonPaths(entry.getValue(), childPath, dataFile, paths, origins);
                }
            }
            case JsonArray arr when !arr.isEmpty() ->
                    collectJsonPaths(arr.getFirst(), currentPath + "[*]", dataFile, paths, origins);
            case null, default -> {
            }
        }
    }
}
