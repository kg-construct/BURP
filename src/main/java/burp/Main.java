package burp;

import burp.model.*;
import burp.model.rdf.*;
import burp.parse.Parse;
import burp.parse.PlanWiring;
import burp.reporting.*;
import burp.util.BURPConfiguration;
import burp.util.Util;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import org.apache.jena.datatypes.BaseDatatype;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.jspecify.annotations.NonNull;
import picocli.CommandLine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {

    public static void main(String[] args) {
        Path cwd = Paths.get("").toAbsolutePath();
        int exit = doMain(args, cwd);
        System.out.println("System exiting with code: " + exit);
        System.exit(exit);
    }

    public static int doMain(String[] args, Path currentWorkingDirectory) {
        BURPConfiguration conf = new BURPConfiguration();
        CommandLine cmd = new CommandLine(conf);
        try {
            cmd.parseArgs(args);
            if (cmd.isUsageHelpRequested()) {
                cmd.usage(System.out);
                return 0;
            } else if (cmd.isVersionHelpRequested()) {
                cmd.printVersionHelp(System.out);
                return 0;
            }
        } catch (CommandLine.ParameterException ex) {
            System.err.println(ex.getMessage());
            ex.getCommandLine().usage(System.err);
            return 1;
        }

        return doMain(
                conf.mappingFile,
                conf.outputFile,
                conf.getOutputFormat(),
                conf.reportFile,
                conf.baseIRI,
                currentWorkingDirectory
        );
    }

    public static RmlExecutionReport report;
    public static String baseIRI;
    public static Path mappingFile;

    public static int doMain(
            String mappingFilePath,
            String outputFilePath,
            Lang outputFormat,
            String reportFilePath,
            String baseIRIValue,
            Path currentWorkingDirectory
    ) {
        report = new RmlExecutionReport();
        baseIRI = baseIRIValue;
        mappingFile = Path.of(mappingFilePath);

        try {
            Parse parser = new Parse();
            List<TriplesMap> triplesMaps;
            try {
                triplesMaps = parser.parseMappingFile(mappingFile, currentWorkingDirectory);
            } catch (BurpException burpException) {
                throw burpException;
            } catch (Exception e) {
                throw new BurpException(new RmlError(
                        "Unknown Error while Parsing " + mappingFilePath,
                        new Origin(),
                        RER.RDFMappingSyntaxError,
                        e
                ));
            }
            if (triplesMaps.isEmpty()) {
                report.getErrors().add(Errors.NoTriplesMap());
            }

            // Wire AST tree
            MappingDocument document = new MappingDocument(triplesMaps);
            PlanWiring.wire(document);
            report.setExecutionPlan(document);

            Map<TriplesMap, Long> statementsPerMap = new HashMap<>();
            for (TriplesMap map : triplesMaps) {
                statementsPerMap.put(map, map.countGeneratedStatements);
            }
            report.getStatistics().setGeneratedStatementPerTriplesMap(statementsPerMap);

            List<RdfStatement> statements = document.generate();

            statementsPerMap = new HashMap<>();
            for (TriplesMap map : triplesMaps) {
                statementsPerMap.put(map, map.countGeneratedStatements);
            }
            report.getStatistics().setGeneratedStatementPerTriplesMap(statementsPerMap);

            List<RdfStatement> defaultStatements = new ArrayList<>();
            Map<LogicalTarget, List<RdfStatement>> statementsByTarget = new HashMap<>();

            for (RdfStatement stmtLike : statements) {
                if (stmtLike == null) continue;
                if (stmtLike.targets() == null || stmtLike.targets().isEmpty()) {
                    defaultStatements.add(stmtLike);
                } else {
                    for (LogicalTarget target : stmtLike.targets()) {
                        statementsByTarget.computeIfAbsent(target, k -> new ArrayList<>()).add(stmtLike);
                    }
                }
            }

            // Write default statements if there are any, or if we have an explicit output file (to create an empty file if needed)
            if (!defaultStatements.isEmpty() || outputFilePath != null) {
                Lang lang = outputFormat != null ? outputFormat :
                        (outputFilePath != null ? RDFLanguages.pathnameToLang(outputFilePath) : null);
                if (lang == null) lang = Lang.NQ;

                if (outputFilePath != null) {
                    try (FileOutputStream output = new FileOutputStream(outputFilePath)) {
                        writeStatements(output, defaultStatements, lang, StandardCharsets.UTF_8);
                    }
                } else {
                    writeStatements(System.out, defaultStatements, lang, StandardCharsets.UTF_8);
                }
            }

            // Write target statements
            for (var entry : statementsByTarget.entrySet()) {
                LogicalTarget target = entry.getKey();
                List<RdfStatement> stmts = entry.getValue();
                RMLTarget t = target.getTarget();
                if (t instanceof FilePathTarget fpt) {
                    File resolvedPath;
                    if (RML.MappingDirectory.equals(fpt.getRoot())) {
                        resolvedPath = mappingFile.getParent().resolve(fpt.getPath()).toFile();
                    } else {
                        resolvedPath = currentWorkingDirectory.resolve(fpt.getPath()).toFile();
                    }

                    Lang targetLang = null;
                    if (target.getSerialization() != null)
                        targetLang = getTargetLang(target.getSerialization(), resolvedPath);
                    if (targetLang == null) targetLang = RDFLanguages.pathnameToLang(resolvedPath.getName());
                    if (targetLang == null) targetLang = Lang.NQ;

                    Charset targetEncoding = StandardCharsets.UTF_8;
                    if (target.getEncoding() != null)
                        targetEncoding = getStandardCharsets(target.getEncoding());

                    if (resolvedPath.getParentFile() != null)
                        resolvedPath.getParentFile().mkdirs();

                    final Lang finalLang = targetLang;
                    final Charset finalEncoding = targetEncoding;
                    Util.writeCompressedFile(resolvedPath, target.getCompression(), output -> {
                        writeStatements(output, stmts, finalLang, finalEncoding);
                    });
                }
            }
        } catch (BurpException e) {
            report.getErrors().add(e.getError());
        } catch (Exception e) {
            report.getErrors().add(Errors.UnexpectedError(e));
        } finally {
            System.out.println(report.toString());
            if (reportFilePath != null && !reportFilePath.isBlank()) {
                RdfReportGenerator.generateRdfReport(report, reportFilePath);
            }
        }

        return report.getErrors().isEmpty() ? 0 : 1;
    }

    private static Lang getTargetLang(Resource targetSerialization, File resolvedPath) {
        return switch (targetSerialization.getURI()) {
            case "http://www.w3.org/ns/formats/N-Quads" -> Lang.NQ;
            case "http://www.w3.org/ns/formats/N-Triples" -> Lang.NT;
            case "http://www.w3.org/ns/formats/Turtle" -> Lang.TURTLE;
            case "http://www.w3.org/ns/formats/JSON-LD" -> Lang.JSONLD;
            case "http://www.w3.org/ns/formats/RDF_XML" -> Lang.RDFXML;
            case "http://www.w3.org/ns/formats/RDF_JSON" -> Lang.RDFJSON;
            case "http://www.w3.org/ns/formats/TriG" -> Lang.TRIG;
            default -> RDFLanguages.pathnameToLang(resolvedPath.getName());
        };
    }

    private static @NonNull Charset getStandardCharsets(Resource encoding) {
        if (encoding.equals(RML.UTF8)) return StandardCharsets.UTF_8;
        else if (encoding.equals(RML.UTF16)) return StandardCharsets.UTF_16;
        return StandardCharsets.UTF_8;
    }

    private static void writeStatements(OutputStream output, List<RdfStatement> statements, Lang lang, Charset encoding) {
        if (lang == Lang.NQ || lang == Lang.NT) {
            NQuadsWriter.write(output, statements, encoding);
            report.getStatistics().setGeneratedStatements((long) statements.size());
            return;
        }

        Dataset ds = generateDataset(statements);
        if (RDFLanguages.isQuads(lang)) {
            RDFDataMgr.write(output, ds, lang);
        } else {
            RDFDataMgr.write(output, ds.getDefaultModel(), lang);
        }
    }

    private static Dataset generateDataset(List<RdfStatement> statements) {
        Dataset ds = DatasetFactory.create();

        Map<String, Resource> bnodeMap = new HashMap<>();

        for (RdfStatement stmt : statements) {
            IRITerm graph = stmt.getGraph();
            Model model;
            if (graph == null || RML.defaultGraph.getURI().equals(graph.uri())) {
                model = ds.getDefaultModel();
            } else {
                model = ds.getNamedModel(graph.uri());
            }

            BlankNodeOrIRI sub = stmt.getSubject();
            Resource s;
            if (sub instanceof IRITerm) {
                s = ResourceFactory.createResource(((IRITerm) sub).uri());
            } else if (sub instanceof BlankNodeTerm) {
                String id = ((BlankNodeTerm) sub).id();
                s = bnodeMap.computeIfAbsent(id, k -> ResourceFactory.createResource());
            } else {
                throw new RuntimeException("Subject must be URI or BlankNode");
            }

            Property p = ResourceFactory.createProperty(stmt.getPredicate().uri());

            Term obj = stmt.getObject();
            RDFNode o;
            switch (obj) {
                case IRITerm iriTerm -> o = ResourceFactory.createResource(iriTerm.uri());
                case BlankNodeTerm blankNodeTerm -> {
                    String id = blankNodeTerm.id();
                    o = bnodeMap.computeIfAbsent(id, k -> ResourceFactory.createResource());
                }
                case LiteralTerm lit -> {
                    if (lit.language() != null) {
                        o = ResourceFactory.createLangLiteral(lit.value(), lit.language());
                    } else if (lit.datatype() != null) {
                        o = ResourceFactory.createTypedLiteral(lit.value(), new BaseDatatype(lit.datatype().uri()));
                    } else {
                        o = ResourceFactory.createTypedLiteral(lit.value());
                    }
                }
                case null, default -> throw new RuntimeException("Unsupported object term " + obj);
            }
            model.add(s, p, o);
        }

        long count = ds.getDefaultModel().size();
        Iterator<String> names = ds.listNames();
        while (names.hasNext()) {
            count += ds.getNamedModel(names.next()).size();
        }
        report.getStatistics().setGeneratedStatements(count);

        return ds;
    }
}
