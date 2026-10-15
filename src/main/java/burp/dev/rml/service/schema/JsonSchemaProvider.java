package burp.dev.rml.service.schema;

import burp.dev.rml.service.completion.ReferenceSchemaResolver.SchemaInfo;
import burp.ls.JSONSourceRFC;
import burp.model.AbstractLogicalSource;
import burp.vocabularies.Rml;
import kotlinx.serialization.json.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

@NullMarked
public class JsonSchemaProvider implements SchemaProvider {

    @Override
    public boolean supports(@Nullable AbstractLogicalSource logicalSource) {
        return logicalSource instanceof JSONSourceRFC js && (js.jsonSchemaFile != null || js.jsonSchemaString != null);
    }

    @Override
    public SchemaInfo extractSchema(AbstractLogicalSource logicalSource) {
        Map<String, String> candidateValues = new LinkedHashMap<>();
        Map<String, ReferenceCompletionOrigin> candidateOrigins = new LinkedHashMap<>();
        boolean hasKnownSchemaOrData = false;

        if (logicalSource instanceof JSONSourceRFC js) {
            String schemaContent = null;
            String schemaLocation = "schema.json";
            if (js.jsonSchemaFile != null) {
                try {
                    java.io.File sf = js.jsonSchemaFile.getFile(js.fileOriginStmts);
                    if (sf != null && sf.exists()) {
                        schemaContent = Files.readString(sf.toPath());
                        schemaLocation = sf.getName();
                    }
                } catch (Exception ignored) {
                }
            } else if (js.jsonSchemaString != null) {
                schemaContent = js.jsonSchemaString;
                schemaLocation = "inline-schema";
            }

            if (schemaContent != null) {
                try {
                    JsonElement schemaElem = Json.Default.parseToJsonElement(schemaContent);
                    collectJsonSchemaProperties(schemaElem, "$", schemaLocation + "#", candidateValues, candidateOrigins);
                    hasKnownSchemaOrData = !candidateValues.isEmpty();
                } catch (Exception ignored) {
                }
            }
        }

        return new SchemaInfo(Rml.JSONPath, candidateValues, candidateOrigins, hasKnownSchemaOrData);
    }

    private void collectJsonSchemaProperties(
            JsonElement elem,
            String currentPath,
            String currentPointer,
            Map<String, String> paths,
            Map<String, ReferenceCompletionOrigin> origins
    ) {
        if (!(elem instanceof JsonObject obj)) return;

        JsonElement propsElem = obj.get("properties");
        if (propsElem instanceof JsonObject propsObj) {
            for (var entry : propsObj.entrySet()) {
                String propKey = entry.getKey();
                String childPath = currentPath.equals("$") ? "$." + propKey : currentPath + "." + propKey;
                String childPointer = currentPointer + "/properties/" + propKey;

                String example = null;
                if (entry.getValue() instanceof JsonObject propDef) {
                    if (propDef.get("example") instanceof JsonPrimitive exPrim) {
                        example = exPrim.getContent();
                    } else if (propDef.get("examples") instanceof JsonArray examplesArr && !examplesArr.isEmpty()) {
                        example = examplesArr.getFirst().toString().replaceAll("^\"|\"$", "");
                    }
                }

                paths.put(childPath, example != null ? example : "");
                origins.put(childPath, new ReferenceCompletionOrigin("schema", null, childPointer, null));

                collectJsonSchemaProperties(entry.getValue(), childPath, childPointer, paths, origins);
            }
        }
    }
}
