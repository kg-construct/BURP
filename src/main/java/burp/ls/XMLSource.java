package burp.ls;

import burp.model.Iteration;
import burp.model.Reference;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import net.sf.saxon.s9api.*;
import org.apache.jena.rdf.model.Resource;

import javax.xml.transform.stream.StreamSource;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XMLSource extends FileBasedLogicalSource {
    private List<Iteration> iterations = null;
    public List<Iteration> getIterations() { return iterations; }
    public void setIterations(List<Iteration> i) { iterations = i; }
    public String iterator;
    public Origin iteratorOrigin;
    public Map<String, String> prefixMap;
    private XPathCompiler xPathCompiler;

    public static final Processor processor = new Processor(false);
    public static final DocumentBuilder documentBuilder = processor.newDocumentBuilder();

    @Override
    public Iterable<Iteration> iterator() {
        try {
            if (getIterations() == null) {
                setIterations(new ArrayList<>());

                XdmNode xmlDocument;
                try (BufferedReader reader = Files.newBufferedReader(Paths.get(getDecompressedFile()), StandardCharsets.UTF_8)) {
                    xmlDocument = documentBuilder.build(new StreamSource(reader));
                }

                xPathCompiler = processor.newXPathCompiler();
                if (prefixMap != null) {
                    for (Map.Entry<String, String> entry : prefixMap.entrySet()) {
                        xPathCompiler.declareNamespace(entry.getKey(), entry.getValue());
                    }
                }

                XPathSelector selector = xPathCompiler.compile(iterator).load();
                selector.setContextItem(xmlDocument);

                for (XdmItem item : selector) {
                    getIterations().add(new XMLIteration(item, getNulls(), xPathCompiler));
                }
            }
            return getIterations();
        } catch (SaxonApiException e) {
            throw new BurpException(
                new RmlError(
                    e.getMessage() != null ? e.getMessage() : "SaxonApiException",
                    iteratorOrigin,
                    RER.ReferenceFormulationSyntaxError,
                    e,
                    new HashMap<>()
                )
            );
        } catch (Exception e) {
            throw new BurpException(
                new RmlError(
                    e.getMessage() != null ? e.getMessage() : "Exception",
                    iteratorOrigin,
                    RER.ReferenceFormulationExecutionError,
                    e,
                    new HashMap<>()
                )
            );
        }
    }

    @Override
    public Resource getReferenceFormulation() {
        return RML.XPath;
    }

    @Override
    public void setReferenceFormulation(Resource value) {
    }

    @Override
    public Reference buildExportedReference(String reference, Origin origin) {
        return new XMLReference(reference, origin);
    }
}
