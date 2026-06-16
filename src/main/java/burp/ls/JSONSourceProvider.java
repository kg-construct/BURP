package burp.ls;

import at.asitplus.jsonpath.JsonPath;
import at.asitplus.jsonpath.core.JsonPathCompilerException;
import at.asitplus.jsonpath.core.NodeListEntry;
import at.asitplus.jsonpath.implementation.AntlrJsonPathCompiler;
import burp.model.Iteration;
import burp.model.LogicalSource;
import burp.model.Reference;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import com.google.auto.service.AutoService;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonElement;
import org.apache.jena.rdf.model.Resource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@AutoService(LogicalSourceProvider.class)
public class JSONSourceProvider implements LogicalSourceProvider {

    @Override
    public boolean supports(Resource referenceFormulation) {
        return RML.JSONPath.equals(referenceFormulation);
    }

    @Override
    public LogicalSource create(Resource ls, Path mappingDirectory, Path currentWorkingDirectory) {
        Resource source = ls.getPropertyResourceValue(RML.source);
        String iterator = ls.getProperty(RML.iterator).getLiteral().getString();

        FileBaseSourceProvider.FileAndOrigin fileAndOrigin = FileBaseSourceProvider.getFile(source, mappingDirectory, currentWorkingDirectory);

        JSONSourceRFC jsonSource = new JSONSourceRFC();
        jsonSource.file = fileAndOrigin.file;
        jsonSource.fileOriginStmts = fileAndOrigin.origin;
        jsonSource.iterator = iterator;
        jsonSource.encoding = FileBaseSourceProvider.getEncoding(source);
        jsonSource.compression = FileBaseSourceProvider.getCompression(source);
        jsonSource.getNulls().addAll(FileBaseSourceProvider.getNullValues(source));

        return jsonSource;
    }

    @Override
    public List<Iteration> parseStringPayload(String payload, String iterator, Origin referenceFormulationOrigin) {
        try {
            JsonElement jsonContent = Json.Default.parseToJsonElement(payload);
            if (iterator == null) {
                throw new BurpException(
                    new RmlError(
                        "Iterator is null", referenceFormulationOrigin,
                        RER.MappingError
                    )
                );
            }
            JsonPath jsonPath = new JsonPath(
                iterator, new AntlrJsonPathCompiler(), s -> null
            );
            List<NodeListEntry> results = jsonPath.query(jsonContent);
            List<Iteration> iterationList = new ArrayList<>();
            for (NodeListEntry it : results) {
                iterationList.add(new JSONIteration(it, Collections.emptySet()));
            }
            return iterationList;
        } catch (Exception e) {
            if (e instanceof BurpException) throw (BurpException) e;
            if (e instanceof JsonPathCompilerException || e instanceof IllegalArgumentException) {
                throw new BurpException(
                    new RmlError(
                        "Syntax error in JSONPath `" + iterator + "`",
                        referenceFormulationOrigin,
                        RER.ReferenceFormulationSyntaxError,
                        e
                    )
                );
            }
            throw new BurpException(
                new RmlError(
                    "Unexpected Error while changing iterator to JSONPath, iteration content " + payload + ".",
                    referenceFormulationOrigin,
                    RER.Error,
                    e
                )
            );
        }
    }

    @Override
    public Reference buildReference(String reference, Origin origin, Origin referenceFormulationOrigin) {
        return new JSONPathReference(reference, origin);
    }
}
