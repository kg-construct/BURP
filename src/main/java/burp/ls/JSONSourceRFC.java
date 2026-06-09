package burp.ls;

import at.asitplus.jsonpath.JsonPath;
import at.asitplus.jsonpath.core.JsonPathCompilerException;
import at.asitplus.jsonpath.core.JsonPathQueryException;
import at.asitplus.jsonpath.core.NodeListEntry;
import at.asitplus.jsonpath.implementation.AntlrJsonPathCompiler;
import burp.model.Iteration;
import burp.model.Reference;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import kotlinx.serialization.json.*;
import org.apache.jena.rdf.model.Resource;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class JSONSourceRFC extends FileBasedLogicalSource {
    public String iterator;
    public Origin iteratorOrigin;



    @Override
    public Iterable<Iteration> iterator() {
        try {
            String decompressedFile = getDecompressedFile();

            String jsonString;
            if (decompressedFile.endsWith(".bson")) {
                throw new RuntimeException("BSON not supported.");
            } else {
                jsonString = Files.readString(Paths.get(decompressedFile), encoding);
            }

            JsonElement jsonContent = Json.Default.parseToJsonElement(jsonString);

            JsonPath jsonPath = new JsonPath(iterator, new AntlrJsonPathCompiler(), JsonPath.Companion.getDefaultFunctionExtensionRepository()::getExtension);
            List<NodeListEntry> results = jsonPath.query(jsonContent);

            List<Iteration> iterationList = new ArrayList<>();
            for (NodeListEntry it : results) {
                iterationList.add(new JSONIteration(it, getNulls()));
            }
            return iterationList;
        } catch (BurpException burpException) {
            throw burpException;
        } catch (Exception exception) {
            String exName = exception.getClass().getName();
            if (exName.equals("at.asitplus.jsonpath.core.JsonPathCompilerException") || 
                exName.equals("at.asitplus.jsonpath.implementation.JsonPathParserException") ||
                exception.getCause() != null && (
                    exception.getCause().getClass().getName().equals("at.asitplus.jsonpath.core.JsonPathCompilerException") ||
                    exception.getCause().getClass().getName().equals("at.asitplus.jsonpath.implementation.JsonPathParserException")
                )) {
                throw new BurpException(new RmlError("Syntax error in JSONPath iterator.", new Origin(this, null), RER.ReferenceFormulationSyntaxError, exception, null));
            }
            throw new BurpException(new RmlError("Error in JSON preparation of iterator.", new Origin(this, null), RER.LogicalSourceError, exception, null));
        }
    }

    @Override
    public Resource getReferenceFormulation() {
        return RML.JSONPath;
    }

    @Override
    public Reference buildExportedReference(String reference, Origin origin) {
        return new JSONPathReference(reference, origin);
    }

}

class JSONPathReference extends Reference {
    private final JsonPath compiledPath;

    public JSONPathReference(String reference, Origin origin) {
        super(reference, origin);
        JsonPath cp = null;
        try {
            if (reference != null) {
                cp = new JsonPath(reference, new AntlrJsonPathCompiler(), JsonPath.Companion.getDefaultFunctionExtensionRepository()::getExtension);
            }
        } catch (Exception ex) {
            if (ex instanceof JsonPathCompilerException) {
                throw new BurpException(new RmlError("Syntax error in JSONPath `" + reference + "`", origin, RER.ReferenceFormulationSyntaxError, ex));
            } else if (ex instanceof BurpException burpException) {
                throw burpException;
            } else {
                throw new BurpException(new RmlError("Unexpected Error in JSONPath preprocessing.", origin, RER.Error, ex));
            }
        }
        this.compiledPath = cp;
    }

    @Override
    public List<Object> getValues(Iteration i) {
        assert i instanceof JSONIteration : "JSONPathReference can only be used with JSONIteration.";

        JSONIteration jsonIteration = (JSONIteration) i;
        if (compiledPath == null) return new ArrayList<>();

        List<Object> resultList = new ArrayList<>();
        try {
            List<NodeListEntry> entries = compiledPath.query(jsonIteration.json.getValue());
            for (NodeListEntry entry : entries) {
                JsonElement jsonElement = entry.getValue();
                switch (jsonElement) {
                    case JsonArray jsonElements -> throw new BurpException(
                            new RmlError(
                                    "Data error: reference retrieved an array with `" + reference + "`",
                                    origin,
                                    RER.ReferenceFormulationExecutionError
                            )
                    );
                    case JsonObject jsonObject -> resultList.add(jsonElement.toString());
                    case JsonNull jsonNull -> {
                        // ignore nulls
                    }
                    case JsonPrimitive p -> {
                        Object content;
                        if (p.isString()) {
                            content = p.getContent();
                        } else {
                            String strContent = p.getContent();
                            try {
                                if (strContent.contains(".")) {
                                    content = Double.parseDouble(strContent);
                                } else if (strContent.equalsIgnoreCase("true") || strContent.equalsIgnoreCase("false")) {
                                    content = Boolean.parseBoolean(strContent);
                                } else {
                                    content = Long.parseLong(strContent);
                                }
                            } catch (NumberFormatException ignored) {
                                content = strContent;
                            }
                        }
                        if (!jsonIteration.getNulls().contains(content)) {
                            resultList.add(content);
                        }
                    }
                    default -> {
                    }
                }
            }
        } catch (Exception ex) {
            if (ex instanceof JsonPathQueryException) {
                throw new BurpException(
                        new RmlError(
                                "Execution error in JSONPath `" + reference + "`", origin, RER.ReferenceFormulationExecutionError, ex
                        )
                );
            } else if (ex instanceof BurpException) {
                throw (BurpException) ex;
            } else {
                throw new BurpException(new RmlError("Unexpected Error", origin, RER.Error, ex));
            }
        }
        return resultList;
    }
}

class JSONIteration extends Iteration {
    public final NodeListEntry json;

    public JSONIteration(NodeListEntry json, Set<Object> nulls) {
        super(nulls);
        this.json = json;
    }

    @Override
    public String asString() {
        return json.getValue().toString();
    }
}


