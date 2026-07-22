package burp.ls;

import at.asitplus.jsonpath.JsonPath;
import at.asitplus.jsonpath.core.JsonPathFilterExpressionType;
import at.asitplus.jsonpath.core.JsonPathFunctionExtension;
import at.asitplus.jsonpath.core.NodeListEntry;
import at.asitplus.jsonpath.implementation.AntlrJsonPathCompiler;
import at.asitplus.jsonpath.implementation.AntlrJsonPathCompilerErrorListener;
import burp.model.Iteration;
import burp.model.PlanNode;
import burp.model.Reference;
import burp.parse.turtleprov.Point;
import burp.reporting.*;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import kotlin.Pair;
import kotlinx.serialization.json.*;
import org.antlr.v4.kotlinruntime.Parser;
import org.antlr.v4.kotlinruntime.RecognitionException;
import org.antlr.v4.kotlinruntime.Recognizer;
import org.antlr.v4.kotlinruntime.atn.ATNConfigSet;
import org.antlr.v4.kotlinruntime.dfa.DFA;
import org.apache.jena.rdf.model.Resource;
import org.bson.RawBsonDocument;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

public class JSONSourceRFC extends FileBasedLogicalSource {
    public String iterator;
    public StatementParts iteratorOrigin;

    @Override
    public Iterable<Iteration> iterator() {
        try {
            String decompressedFile = getDecompressedFile();
            Path decompressedFilePath = Paths.get(decompressedFile);

            String jsonString;
            if (decompressedFile.endsWith(".bson")) {
                byte[] bsonBytes = Files.readAllBytes(decompressedFilePath);
                RawBsonDocument bsonDoc = new RawBsonDocument(bsonBytes);
                JsonWriterSettings settings = JsonWriterSettings.builder()
                        .outputMode(JsonMode.RELAXED)
                        .build();
                jsonString = bsonDoc.toJson(settings);
            } else {
                jsonString = Files.readString(decompressedFilePath, encoding);
            }

            JsonElement jsonContent = Json.Default.parseToJsonElement(jsonString);

            CapturingErrorListener listener = new CapturingErrorListener();
            JsonPath jsonPath;
            try {
                jsonPath = new JsonPath(iterator, new AntlrJsonPathCompiler(listener), JsonPath.Companion.getDefaultFunctionExtensionRepository()::getExtension);
            } catch (Exception e) {
                if (!listener.getErrors().isEmpty()) {
                    JSONPathError firstError = listener.getErrors().getFirst();
                    RmlError rmlError = firstError.getRmlError(e, iterator, iteratorOrigin, this);
                    throw new BurpException(rmlError);
                }
                throw e;
            }
            List<NodeListEntry> results = jsonPath.query(jsonContent);

            List<Iteration> iterationList = new ArrayList<>();
            for (NodeListEntry it : results) {
                iterationList.add(new JSONIteration(it, getNulls()));
            }
            return iterationList;
        } catch (BurpException burpException) {
            throw burpException;
        } catch (Exception exception) {
            throw new BurpException(new RmlError("Error in JSON preparation of iterator.", new Origin(this, List.of(iteratorOrigin)), RER.LogicalSourceError, exception, null));
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
        CapturingErrorListener listener = new CapturingErrorListener();
        try {
            if (reference != null) {
                cp = new JsonPath(reference, new AntlrJsonPathCompiler(listener), JsonPath.Companion.getDefaultFunctionExtensionRepository()::getExtension);
            }
        } catch (BurpException be) {
            throw be;
        } catch (Exception ex) {
            if (!listener.getErrors().isEmpty()) {
                JSONPathError firstError = listener.getErrors().getFirst();
                RDFGraphPointer rdfPointer = origin.sourceStatements().stream().map(it -> it instanceof RDFGraphPointer gp ? gp : null).filter(Objects::nonNull).findFirst().orElseGet(null);
                var error = firstError.getRmlError(ex, reference, rdfPointer, this);
                throw new BurpException(error);
            }
            throw new BurpException(new RmlError("Unexpected Error in JSONPath preprocessing.", origin, RER.Error, ex));
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
                    case JsonArray jsonElements ->
                        // TODO In the future we may want to support cdt:List and cdt:Map.
                            throw new BurpException(new RmlError("Data error: reference retrieved an array with `" + reference + "`", origin, RER.ReferenceFormulationExecutionError));
                    case JsonObject jsonObject -> resultList.add(jsonElement.toString());
                    case JsonNull jsonNull -> {
                        // ignore nulls
                    }
                    case JsonPrimitive p -> {
                        Object content = parseJsonPrimitive(p);
                        if (!jsonIteration.getNulls().contains(content)) resultList.add(content);
                    }
                    default -> {
                    }
                }
            }
        } catch (BurpException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BurpException(new RmlError("Unexpected Error in JSONPath evaluation.", origin, RER.ReferenceFormulationExecutionError, ex));
        }
        return resultList;
    }

    public static Object parseJsonPrimitive(JsonPrimitive p) {
        if (p.isString()) {
            return p.getContent();
        } else if (JsonElementKt.getIntOrNull(p) != null) {
            return JsonElementKt.getInt(p);
        } else if (JsonElementKt.getLongOrNull(p) != null) {
            return JsonElementKt.getLong(p);
        } else if (JsonElementKt.getFloatOrNull(p) != null) {
            return JsonElementKt.getFloat(p);
        } else if (JsonElementKt.getDoubleOrNull(p) != null) {
            return JsonElementKt.getDouble(p);
        } else if (JsonElementKt.getBooleanOrNull(p) != null) {
            return JsonElementKt.getBoolean(p);
        }
        return null;
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

record JSONPathError(@Nullable Point start, @NonNull String message, boolean isSyntaxError) {
    @NonNull RmlError getRmlError(@NonNull Exception compilerException, @Nullable String jsonPath, @NonNull RDFGraphPointer jsonPathPtr, @NonNull PlanNode planNode) {
        Origin origin = new Origin(planNode, List.of(jsonPathPtr));
        if (start != null) {
            LiteralPart literalPart;
            switch (jsonPathPtr) {
                case LiteralPart lp -> literalPart = lp;
                case StatementParts sp -> literalPart = sp.toLiteralPart();
                default -> literalPart = null;
            }

            if (literalPart != null) {
                PointRange newRange = literalPart.objectRange().plus(new PointRange(start));
                var newRangeLiteralPart = new LiteralPart(literalPart.stmt(), newRange);
                origin = new Origin(planNode, List.of(newRangeLiteralPart));
            }
        }

        return new RmlError(
                message,
                origin,
                isSyntaxError ? RER.ReferenceFormulationSyntaxError : RER.ReferenceFormulationExecutionError,
                compilerException,
                null
        );
    }
}

class CapturingErrorListener implements AntlrJsonPathCompilerErrorListener {
    private final List<JSONPathError> errors = new ArrayList<>();

    public List<JSONPathError> getErrors() {
        return errors;
    }

    @Override
    public void unknownFunctionExtension(@NonNull String functionExtensionName) {
        errors.add(new JSONPathError(null, "Unknown JSONPath function extension: \"" + functionExtensionName + "\"", false));
    }

    @Override
    public void invalidFunctionExtensionForTestExpression(@NonNull String functionExtensionName) {
        errors.add(new JSONPathError(null, "Invalid JSONPath function extension return type for test expression: \"" + functionExtensionName + "\"", false));
    }

    @Override
    public void invalidFunctionExtensionForComparable(@NonNull String functionExtensionName) {
        errors.add(new JSONPathError(null, "Invalid JSONPath function extension return type for comparable expression: \"" + functionExtensionName + "\"", false));
    }

    @Override
    public void invalidArglistForFunctionExtension(
            @NonNull String functionExtensionName,
            @NonNull JsonPathFunctionExtension functionExtensionImplementation,
            @NonNull List<? extends Pair<? extends JsonPathFilterExpressionType, String>> coercedArgumentTypes
    ) {
        List<String> firsts = new ArrayList<>();
        List<String> seconds = new ArrayList<>();
        for (Object obj : coercedArgumentTypes) {
            if (obj instanceof Pair<?, ?> pair) {
                firsts.add(String.valueOf(pair.getFirst()));
                seconds.add(String.valueOf(pair.getSecond()));
            }
        }
        String expected = stream(functionExtensionImplementation.getArgumentTypes())
                .map(String::valueOf)
                .collect(joining(", "));
        String receivedFirst = String.join(", ", firsts);
        String receivedSecond = String.join(", ", seconds);
        String msg = "Invalid arguments for function extension \"" + functionExtensionName + "\": Expected: <" + expected + ">, but received <" + receivedFirst + ">: <" + receivedSecond + ">";
        errors.add(new JSONPathError(null, msg, false));
    }


    @Override
    public void invalidTestExpression(@NonNull String testContextString) {
        errors.add(new JSONPathError(null, "Invalid test expression: " + testContextString, false));
    }


    @Override
    public void syntaxError(@NonNull Recognizer<?, ?> recognizer, @Nullable Object offendingSymbol, int line, int charPositionInLine, @NonNull String msg, @Nullable RecognitionException e) {
        errors.add(new JSONPathError(new Point(line - 1, charPositionInLine), msg, true));
    }

    @Override
    public void reportAmbiguity(@NonNull Parser recognizer, @NonNull DFA dfa, int startIndex, int stopIndex, boolean exact, @NonNull BitSet ambigAlts, @NonNull ATNConfigSet configs) {
        // noop
    }

    @Override
    public void reportAttemptingFullContext(@NonNull Parser recognizer, @NonNull DFA dfa, int startIndex, int stopIndex, @NonNull BitSet ambigAlts, @NonNull ATNConfigSet configs) {
        // noop
    }

    @Override
    public void reportContextSensitivity(@NonNull Parser recognizer, @NonNull DFA dfa, int startIndex, int stopIndex, int i2, @NonNull ATNConfigSet configs) {
        // noop
    }
}
