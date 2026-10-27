package burp.dev.rml.service.schema;

import burp.dev.rml.service.completion.ReferenceSchemaResolver.SchemaInfo;
import burp.dev.rml.service.eval.EvaluationContexts;
import burp.ls.FileBasedLogicalSource;
import burp.ls.JSONSourceRFC;
import burp.model.AbstractLogicalSource;
import burp.model.Iteration;
import burp.model.PlanNode;
import burp.vocabularies.Rml;
import kotlinx.serialization.json.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Extracts candidate JSONPath references and sample values from runtime sample iterations of JSON sources.
 */
@NullMarked
public class JsonSampledSchemaProvider implements SchemaProvider {

    private final @Nullable Integer iterationSampleLimit;

    public JsonSampledSchemaProvider(@Nullable Integer iterationSampleLimit) {
        this.iterationSampleLimit = iterationSampleLimit;
    }

    @Override
    public boolean supports(@Nullable AbstractLogicalSource logicalSource) {
        return logicalSource instanceof JSONSourceRFC;
    }

    @Override
    public SchemaInfo extractSchema(AbstractLogicalSource logicalSource) {
        return extractSchema(logicalSource, null);
    }

    public SchemaInfo extractSchema(AbstractLogicalSource logicalSource, @Nullable PlanNode targetNode) {
        Map<String, String> candidateValues = new LinkedHashMap<>();
        Map<String, ReferenceCompletionOrigin> candidateOrigins = new LinkedHashMap<>();
        boolean hasKnownSchemaOrData = false;

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
                String dataFile = logicalSource instanceof FileBasedLogicalSource fbls && fbls.file != null
                        ? fbls.file.toString()
                        : "data";
                for (Iteration sampleIter : sampledIters) {
                    try {
                        JsonElement jsonElem = Json.Default.parseToJsonElement(sampleIter.asString());
                        collectJsonPaths(jsonElem, "$", dataFile, candidateValues, candidateOrigins);
                    } catch (Exception ignored) {
                    }
                }
                hasKnownSchemaOrData = !candidateValues.isEmpty();
            }
        } catch (Exception ignored) {
        }

        return new SchemaInfo(Rml.JSONPath, candidateValues, candidateOrigins, hasKnownSchemaOrData);
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
