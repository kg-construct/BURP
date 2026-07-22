package burp.ls;

import burp.model.Iteration;
import burp.model.LogicalSource;
import burp.model.Reference;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.reporting.StatementPart;
import burp.vocabularies.CSVW;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import com.google.auto.service.AutoService;
import com.opencsv.CSVReader;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
@AutoService(LogicalSourceProvider.class)
public class CSVSourceProvider implements LogicalSourceProvider {
    @Override
    public boolean supports(Resource referenceFormulation) {
        return RML.CSV.equals(referenceFormulation);
    }

    @Override
    public LogicalSource create(Resource ls, Path mappingDirectory, Path currentWorkingDirectory) {
        Resource s = ls.getPropertyResourceValue(RML.source);
        CSVSource source = new CSVSource();
        FileBaseSourceProvider.FileAndOrigin fileAndOrigin = FileBaseSourceProvider.getFile(s, mappingDirectory, currentWorkingDirectory);
        source.file = fileAndOrigin.file;
        source.fileOriginStmts = fileAndOrigin.origin;
        source.compression = FileBaseSourceProvider.getCompression(s);
        
        if (s.hasProperty(RDF.type, CSVW.Table)) {
            // IF IT IS A CSVW TABLE, THEN LOOK FOR THE ENCODING IN THE DIALECT
            if (s.hasProperty(CSVW.dialect)) {
                Resource r = s.getPropertyResourceValue(CSVW.dialect);
                if (r.hasProperty(CSVW.encoding) && !ls.hasProperty(RML.encoding)) {
                    Statement encodingStmt = r.getProperty(CSVW.encoding);
                    String encoding = encodingStmt.getString();
                    switch (encoding) {
                        case "UTF-8":
                            source.encoding = StandardCharsets.UTF_8;
                            break;
                        case "UTF-16":
                            source.encoding = StandardCharsets.UTF_16;
                            break;
                        default:
                            throw new BurpException(
                                new RmlError(
                                    "Provided Character Set " + r + " not supported.",
                                    new Origin(encodingStmt, StatementPart.Predicate, StatementPart.Object),
                                    RER.Error, null
                                )
                            );
                    }
                }

                if (r.hasProperty(CSVW.delimiter)) {
                    String delimStr = r.getProperty(CSVW.delimiter).getString();
                    if (delimStr.equals("\\t")) {
                        source.delimiter = '\t';
                    } else if (delimStr.equals("\\n")) {
                        source.delimiter = '\n';
                    } else if (delimStr.equals("\\r")) {
                        source.delimiter = '\r';
                    } else if (delimStr.length() == 1) {
                        source.delimiter = delimStr.charAt(0);
                    } else {
                        source.delimiter = r.getProperty(CSVW.delimiter).getChar();
                    }
                }

                if (r.hasProperty(CSVW.header)) {
                    source.firstLineIsHeader = r.getProperty(CSVW.header).getBoolean();
                }

                if (r.hasProperty(CSVW.quoteChar)) {
                    source.quoteChar = r.getProperty(CSVW.quoteChar).getChar();
                }

                if (r.hasProperty(CSVW.commentPrefix)) {
                    source.commentPrefix = r.getProperty(CSVW.commentPrefix).getString();
                }

                if (r.hasProperty(CSVW.trim)) {
                    source.trim = r.getProperty(CSVW.trim).getBoolean();
                }
                source.nulls.addAll(getNulls(ls, s));
                source.nulls.addAll(getNulls(ls, r));

                // TODO: Natural RDF mapping of CSV values
            } else {
                source.encoding = StandardCharsets.UTF_8;
            }
        }

        if (ls.hasProperty(RML.encoding)) {
            source.encoding = FileBaseSourceProvider.getEncoding(ls);
        } else if (!s.hasProperty(CSVW.dialect) || !s.getPropertyResourceValue(CSVW.dialect).hasProperty(CSVW.encoding)) {
            source.encoding = FileBaseSourceProvider.getEncoding(ls);
        }
        source.nulls.addAll(FileBaseSourceProvider.getNullValues(s));

        return source;
    }

    private Set<Object> getNulls(Resource ls, Resource resource) {
        if (resource.hasProperty(CSVW.NULL) && !ls.hasProperty(RML.NULL)) {
            return resource.listProperties(CSVW.NULL).mapWith(t -> {
                if (t.getObject().isResource()) {
                    // WE ASSUME WE CAN HAVE RESOURCES AS NULL FOR
                    // SPARQL SOURCES
                    return (t.getObject().asResource());
                } else {
                    return (t.getObject().asLiteral().getValue());
                }
            }).toSet();
        }
        return Collections.emptySet();
    }

    @Override
    public List<Iteration> parseStringPayload(String payload, String iterator, Origin referenceFormulationOrigin) {
        try {
            CSVReader reader = new CSVReader(new StringReader(payload));
            List<String[]> all = reader.readAll();
            reader.close();
            if (all.isEmpty()) return Collections.emptyList();
            String[] header = all.removeFirst();
            return all.stream()
                .map(it -> (Iteration) new CSVIteration(header, it, Collections.emptySet()))
                .collect(Collectors.toList());
        } catch (Exception e) {
            throw new BurpException(
                new RmlError(
                    "Unexpected Error while changing iterator to type CSV, iteration content " + payload + ".",
                    referenceFormulationOrigin,
                    RER.Error,
                    e
                )
            );
        }
    }

    @Override
    public Reference buildReference(String reference, Origin origin, Origin referenceFormulationOrigin) {
        return new CSVReference(reference, origin);
    }
}
