package burp.ls;

import burp.model.Iteration;
import burp.model.LogicalSource;
import burp.model.Reference;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.reporting.StatementPart;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import com.google.auto.service.AutoService;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static burp.ls.FileBaseSourceProvider.*;

@SuppressWarnings("unused")
@AutoService(LogicalSourceProvider.class)
public class XMLSourceProvider implements LogicalSourceProvider {
    @Override
    public boolean supports(Resource referenceFormulation) {
        return RML.XPath.equals(referenceFormulation)
                || referenceFormulation.hasProperty(RDF.type, RML.XPathReferenceFormulation);
    }

    @Override
    public LogicalSource create(Resource ls, Path mappingDirectory, Path currentWorkingDirectory) {
        FileBaseSourceProvider.FileAndOrigin fileAndOrigin = getFile(ls.getPropertyResourceValue(RML.source), mappingDirectory, currentWorkingDirectory);
        XMLSource source = new XMLSource();
        source.file = fileAndOrigin.file;
        source.fileOriginStmts = fileAndOrigin.origin;
        source.iterator = ls.getProperty(RML.iterator).getLiteral().getString();
        source.iteratorOrigin = new Origin(ls.getProperty(RML.iterator), StatementPart.Object);
        source.encoding = getEncoding(ls);
        source.compression = getCompression(ls);
        source.nulls.addAll(getNullValues(ls));
        Resource referenceFormulation = ls.getPropertyResourceValue(RML.referenceFormulation);
        if (referenceFormulation.hasProperty(RDF.type, RML.XPathReferenceFormulation)) {
            source.prefixMap = getPrefixMap(ls);
        }
        return source;
    }

    @Override
    public List<Iteration> parseStringPayload(String payload, String iterator, Origin referenceFormulationOrigin) {
        try {
            var xmlDocument = XMLSource.documentBuilder.build(new StreamSource(new StringReader(payload)));
            var xPathCompiler = XMLSource.processor.newXPathCompiler();
            
            if (iterator == null) {
                throw new BurpException(
                    new RmlError(
                        "Iterator is null",
                        referenceFormulationOrigin, 
                        RER.MappingError
                    )
                );
            }
            
            var selector = xPathCompiler.compile(iterator).load();
            selector.setContextItem(xmlDocument);
            var nodes = selector.evaluate();

            return StreamSupport.stream(nodes.spliterator(), false)
                .map(it -> (Iteration) new XMLIteration(it, Set.of(), xPathCompiler))
                .collect(Collectors.toList());
        } catch (Exception e) {
            if (e instanceof BurpException) throw (BurpException) e;
            throw new BurpException(
                new RmlError(
                    "Unexpected Error while changing iterator to type XPath, iteration content " + payload + ".",
                    referenceFormulationOrigin,
                    RER.Error,
                    e
                )
            );
        }
    }

    @Override
    public Reference buildReference(String reference, Origin origin, Origin referenceFormulationOrigin) {
        return new XMLReference(reference, origin);
    }

    /**
     * Generates prefix map from rml:namespace definitions
     *
     * ls should look like:
     *
     * ls rml:referenceFormulation [
     *    rml:namespace [
     *      rml:namespacePrefix "prefix"
     *      rml:namespaceURL "url"
     *    ]
     * ]
     *
     */
    public static Map<String, String> getPrefixMap(Resource ls) {
        // Get XPathReferenceFormulation
        Resource referenceFormulation = ls.getPropertyResourceValue(RML.referenceFormulation);
        // Set the map of namespaces for XPath iteration
        StmtIterator properties = referenceFormulation.listProperties(RML.namespace);
        Map<String, String> prefixMap = new HashMap<>();
        while (properties.hasNext()) {
            Statement statement = properties.next();
            Resource namespace = statement.getResource();
            String prefixValue = namespace.getProperty(RML.namespacePrefix).getLiteral().getString();
            String urlValue = namespace.getProperty(RML.namespaceURL).getLiteral().getString();
            prefixMap.put(prefixValue, urlValue);
        }
        return prefixMap;
    }
}
