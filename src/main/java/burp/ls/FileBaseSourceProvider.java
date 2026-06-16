package burp.ls;

import burp.reporting.*;
import burp.vocabularies.CSVW;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.RDF;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileBaseSourceProvider {

    public static Resource getCompression(Resource source) {
        Resource compression = source.getPropertyResourceValue(RML.compression);
        if (compression == null || RML.none.equals(compression)) {
            return RML.none;
        }
        if (RML.zip.equals(compression) || RML.gzip.equals(compression) || RML.targz.equals(compression) || RML.tarxz.equals(compression)) {
            return compression;
        }
        throw new RuntimeException("Provided compression " + compression + " not supported.");
    }

    public static Charset getEncoding(Resource source) {
        Resource enc = source.getPropertyResourceValue(RML.encoding);
        if (enc == null || RML.UTF8.equals(enc)) {
            return StandardCharsets.UTF_8;
        }
        if (RML.UTF16.equals(enc)) {
            return StandardCharsets.UTF_16;
        }
        throw new BurpException(
            new RmlError(
                "Provided Character Set " + enc + " not supported.",
                new Origin(source.getProperty(RML.encoding), StatementPart.Predicate, StatementPart.Object),
                RER.Error
            )
        );
    }

    public static List<Object> getNullValues(Resource source) {
        List<Object> os = new ArrayList<>();
        source.listProperties(RML.NULL).forEachRemaining(stmt -> {
            RDFNode obj = stmt.getObject();
            // WE ASSUME WE CAN HAVE RESOURCES AS NULL FOR SPARQL SOURCES
            if (obj.isResource()) {
                os.add(obj.asResource());
            } else {
                os.add(obj.asLiteral().getValue());
            }
        });
        return os;
    }

    public static class FileAndOrigin {
        public final SourceFile file;
        public final List<StatementParts> origin;

        public FileAndOrigin(SourceFile file, List<StatementParts> origin) {
            this.file = file;
            this.origin = origin;
        }
    }

    public static FileAndOrigin getFile(Resource source, Path mappingDir, Path currentWorkingDir) {
        if (source.hasProperty(RDF.type, RML.RelativePathSource) || source.hasProperty(RDF.type, RML.FilePath)) {
            Statement pathStmt = source.getProperty(RML.path);
            String file;
            if (pathStmt != null && pathStmt.getObject().isLiteral()) {
                file = pathStmt.getLiteral().getString();
            } else {
                Origin origin = pathStmt != null ? new Origin(pathStmt, StatementPart.Object) : null;
                throw new BurpException(new RmlError("The path must be a plain literal", origin, RER.MappingError));
            }

            Statement rootStmt = source.getProperty(RML.root);
            RDFNode root = rootStmt != null ? rootStmt.getObject() : null;

            Path resolved;
            if (RML.MappingDirectory.equals(root)) {
                resolved = mappingDir.resolve(file);
            } else if (root == null || RML.CurrentWorkingDirectory.equals(root)) {
                resolved = currentWorkingDir.resolve(file);
            } else if (root.isLiteral()) {
                var literal = root.asLiteral();
                if (literal.getLanguage() != null && !literal.getLanguage().isEmpty() || literal.getDatatypeURI() != null && !literal.getDatatypeURI().equals("http://www.w3.org/2001/XMLSchema#string")) {
                    throw new RuntimeException("rml:root must be a plain literal or string " + literal);
                }
                resolved = Path.of(literal.getString()).resolve(file);
            } else {
                throw new RuntimeException("RelativePathSource specified root " + root + " is not supported.");
            }

            List<StatementParts> originList = new ArrayList<>();
            if (pathStmt != null) originList.add(StatementParts.fromPredicateObject(pathStmt));
            if (rootStmt != null) originList.add(StatementParts.fromPredicateObject(rootStmt));

            return new FileAndOrigin(new SourceFile.Local(resolved.toString()), originList);
        }

        if (source.hasProperty(RDF.type, DCAT.Distribution)) {
            String url = source.getPropertyResourceValue(DCAT.downloadURL).getURI();
            return new FileAndOrigin(new SourceFile.Remote(url), List.of(StatementParts.fromPredicateObject(source.getProperty(DCAT.downloadURL))));
        }

        if (source.hasProperty(RDF.type, CSVW.Table)) {
            String url = source.getProperty(CSVW.url).getLiteral().getString();
            return new FileAndOrigin(new SourceFile.Remote(url), List.of(StatementParts.fromPredicateObject(source.getProperty(CSVW.url))));
        }

        Resource type = source.getPropertyResourceValue(RDF.type);
        throw new BurpException(
            new RmlError(
                "Source type (" + type + ") not yet implemented in source " + source,
                new Origin(source.getProperty(RDF.type), StatementPart.Object),
                RER.UnsupportedMapping
            )
        );
    }
}
