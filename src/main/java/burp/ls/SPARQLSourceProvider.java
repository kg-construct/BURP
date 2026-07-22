package burp.ls;

import burp.model.LogicalSource;
import burp.reporting.Origin;
import burp.reporting.StatementPart;
import burp.reporting.StatementParts;
import burp.util.Util;
import burp.vocabularies.RML;
import burp.vocabularies.SD;
import com.google.auto.service.AutoService;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.VOID;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import static burp.ls.FileBaseSourceProvider.*;

@SuppressWarnings("unused")
@AutoService(LogicalSourceProvider.class)
public class SPARQLSourceProvider implements LogicalSourceProvider {
    @Override
    public boolean supports(Resource referenceFormulation) {
        return RML.SPARQL_Results_CSV.equals(referenceFormulation)
                || RML.SPARQL_Results_TSV.equals(referenceFormulation)
                || RML.SPARQL_Results_XML.equals(referenceFormulation)
                || RML.SPARQL_Results_JSON.equals(referenceFormulation);
    }

    @Override
    public LogicalSource create(
        Resource ls, Path mappingDirectory, Path currentWorkingDirectory
    ) {
        String iterator = ls.getProperty(RML.iterator).getLiteral().getString();
        Origin iteratorOrigin = new Origin(ls.getProperty(RML.iterator), StatementPart.Object);
        Resource sourceNode = ls.getPropertyResourceValue(RML.source);
        boolean isTSV = RML.SPARQL_Results_TSV.equals(sourceNode.getPropertyResourceValue(RDF.type));
        Resource referenceFormulation = ls.getPropertyResourceValue(RML.referenceFormulation);

        if (sourceNode.hasProperty(RDF.type, VOID.Dataset)) {
            SPARQLFileSource source = new SPARQLFileSource(isTSV, referenceFormulation);
            String file = sourceNode.getPropertyResourceValue(VOID.dataDump).getURI();
            source.file = getAbsoluteOrRelativeFromFileProtocol(file, currentWorkingDirectory);
            source.fileOriginStmts = List.of(StatementParts.fromPredicateObject(sourceNode.getProperty(VOID.dataDump)));
            source.compression = getCompression(sourceNode);
            source.encoding = getEncoding(sourceNode);
            source.iterator = iterator;
            source.iteratorOrigin = iteratorOrigin;
            source.nulls.addAll(getNullValues(sourceNode));
            return source;
        } else if (sourceNode.hasProperty(RDF.type, SD.Service)) {
            SPARQLServiceSource source = new SPARQLServiceSource(isTSV, referenceFormulation);
            source.endpoint = sourceNode.getPropertyResourceValue(SD.endpoint).getURI();
            source.iterator = iterator;
            source.iteratorOrigin = iteratorOrigin;
            source.nulls.addAll(getNullValues(sourceNode));
            return source;
        } else {
            // WE HAVE A SIMPLE SPARQL SOURCE
            SPARQLFileSource source = new SPARQLFileSource(isTSV, referenceFormulation);
            FileBaseSourceProvider.FileAndOrigin fileAndOrigin = getFile(sourceNode, mappingDirectory, currentWorkingDirectory);
            source.file = fileAndOrigin.file;
            source.fileOriginStmts = fileAndOrigin.origin;
            source.compression = getCompression(sourceNode);
            source.encoding = getEncoding(sourceNode);
            source.iterator = iterator;
            source.iteratorOrigin = iteratorOrigin;
            source.nulls.addAll(getNullValues(sourceNode));
            return source;
        }
    }

    private SourceFile getAbsoluteOrRelativeFromFileProtocol(String fileUri, Path rootPath) {
        try {
            URI url = new URI(fileUri);
            if (Util.isValidAndAbsoluteIRI(fileUri)) return new SourceFile.Remote(fileUri);
            if ("file".equals(url.getScheme())) {
                return Path.of(url.getPath()).isAbsolute() ? new SourceFile.Local(url.getPath())
                        : new SourceFile.Local(rootPath.resolve(url.getPath()).toString());
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException(fileUri + " is not a file URL.", e);
        }
    }
}
