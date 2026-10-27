package burp;

import burp.dev.DevServer;
import burp.model.*;
import burp.model.context.EvaluationContext;
import burp.model.rdf.*;
import burp.parse.RmlLoader;
import burp.processor.ExecutionOptions;
import burp.processor.RmlProcessor;
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

        if (conf.isDevServer()) {
            return DevServer.runServerMode(conf);
        }

        List<String> mappingFiles = conf.mappingFiles;
        if (mappingFiles.isEmpty()) {
            System.err.println("Error: Missing required option: '--mappingFile=<mappingFile>'");
            cmd.usage(System.err);
            return 1;
        }

        if (conf.validate) {
            return doValidate(conf, currentWorkingDirectory);
        }

        return doMain(
                mappingFiles,
                conf.outputFile,
                conf.getOutputFormat(),
                conf.reportFile,
                conf.baseIRI,
                currentWorkingDirectory,
                conf.color
        );
    }

    public static int doValidate(BURPConfiguration conf, Path currentWorkingDirectory) {
        RmlExecutionReport report = new RmlExecutionReport();
        List<String> mappingFiles = conf.getEffectiveMappingFiles();
        List<Path> mappingPaths = mappingFiles.stream().map(Path::of).toList();
        boolean conforms;
        try {
            RmlLoader loader = new RmlLoader(mappingPaths)
                    .withCurrentDirectory(currentWorkingDirectory)
                    .withReport(report);
            loader.loadMappingDocument();
            conforms = report.getErrors().isEmpty();
        } catch (BurpException e) {
            conforms = false;
        }

        boolean useColors = conf.color != null ? conf.color : FileHighlight.hasColorSupport();
        System.out.print(PlainTextReportGenerator.generateTextReport(report, useColors));

        if (conf.reportFile != null && !conf.reportFile.isBlank()) {
            RdfReportGenerator.generateRdfReport(report, conf.reportFile);
        }

        return conforms ? 0 : 1;
    }

    public static void reportConfigurationError(String message, String reportFilePath, Exception cause) {
        RmlExecutionReport report = new RmlExecutionReport();
        RmlError error = new RmlError(message, null, RER.ConfigurationError, cause);
        report.getErrors().add(error);

        System.out.print(PlainTextReportGenerator.generateTextReport(report));
        if (reportFilePath != null && !reportFilePath.isBlank()) {
            RdfReportGenerator.generateRdfReport(report, reportFilePath);
        }
    }

    public static int doMain(
            String mappingFilePath,
            String outputFilePath,
            Lang outputFormat,
            String reportFilePath,
            String baseIRI,
            Path currentWorkingDirectory
    ) {
        return doMain(List.of(mappingFilePath), outputFilePath, outputFormat, reportFilePath, baseIRI, currentWorkingDirectory, null);
    }

    public static int doMain(
            String mappingFilePath,
            String outputFilePath,
            Lang outputFormat,
            String reportFilePath,
            String baseIRI,
            Path currentWorkingDirectory,
            @org.jspecify.annotations.Nullable Boolean color
    ) {
        return doMain(List.of(mappingFilePath), outputFilePath, outputFormat, reportFilePath, baseIRI, currentWorkingDirectory, color);
    }

    public static int doMain(
            List<String> mappingFilePaths,
            String outputFilePath,
            Lang outputFormat,
            String reportFilePath,
            String baseIRI,
            Path currentWorkingDirectory,
            @org.jspecify.annotations.Nullable Boolean color
    ) {
        RmlExecutionReport report = new RmlExecutionReport();
        List<Path> mappingFiles = mappingFilePaths.stream().map(Path::of).toList();

        try {
            RmlLoader loader = new RmlLoader(mappingFiles)
                    .withCurrentDirectory(currentWorkingDirectory)
                    .withReport(report);
            MappingDocument document;
            document = loader.loadMappingDocument();
            List<TriplesMap> triplesMaps = document.triplesMaps;
            if (triplesMaps.isEmpty()) {
                report.getErrors().add(Errors.NoTriplesMap());
            }
            report.setExecutionPlan(document);

            Map<TriplesMap, Long> statementsPerMap = new HashMap<>();
            for (TriplesMap map : triplesMaps) {
                statementsPerMap.put(map, map.countGeneratedStatements);
            }
            report.getStatistics().setGeneratedStatementPerTriplesMap(statementsPerMap);

            EvaluationContext evalCtx = EvaluationContext.createDefault().withReport(report).withBaseIri(baseIRI);
            RmlProcessor processor = new RmlProcessor();
            ExecutionOptions options = ExecutionOptions.builder()
                    .baseIri(baseIRI)
                    .useCache(false)
                    .build();
            Map<LogicalTarget, List<RdfStatement>> statementsByTarget = processor.executeToStatements(document, options, evalCtx);

            statementsPerMap = new HashMap<>();
            for (TriplesMap map : triplesMaps) {
                statementsPerMap.put(map, map.countGeneratedStatements);
            }
            report.getStatistics().setGeneratedStatementPerTriplesMap(statementsPerMap);

            List<RdfStatement> defaultStatements = statementsByTarget.getOrDefault(LogicalTarget.DEFAULT, Collections.emptyList());

            // Write default statements if there are any, or if we have an explicit output file (to create an empty file if needed)
            if (!defaultStatements.isEmpty() || outputFilePath != null) {
                Lang lang = outputFormat != null ? outputFormat :
                        (outputFilePath != null ? RDFLanguages.pathnameToLang(outputFilePath) : null);
                if (lang == null) lang = Lang.NQ;

                if (outputFilePath != null) {
                    try (FileOutputStream output = new FileOutputStream(outputFilePath)) {
                        writeStatements(output, defaultStatements, lang, StandardCharsets.UTF_8, report);
                    }
                } else {
                    writeStatements(System.out, defaultStatements, lang, StandardCharsets.UTF_8, report);
                }
            }

            // Write target statements
            for (var entry : statementsByTarget.entrySet()) {
                LogicalTarget target = entry.getKey();
                if (target.isDefault()) continue;
                List<RdfStatement> stmts = entry.getValue();
                RMLTarget t = target.target;
                if (t instanceof FilePathTarget fpt) {
                    File resolvedPath = fpt.getResolvedPath(currentWorkingDirectory, mappingFiles.getFirst());

                    Lang targetLang = null;
                    if (target.serialization != null)
                        targetLang = getTargetLang(target.serialization, resolvedPath);
                    if (targetLang == null) targetLang = RDFLanguages.pathnameToLang(resolvedPath.getName());
                    if (targetLang == null) targetLang = Lang.NQ;

                    Charset targetEncoding = StandardCharsets.UTF_8;
                    if (target.encoding != null)
                        targetEncoding = getStandardCharsets(target.encoding);

                    if (resolvedPath.getParentFile() != null) {
                        boolean directoryCreated = resolvedPath.getParentFile().mkdirs();
                        if (!directoryCreated) {
                            System.out.print("Could not create directory " + resolvedPath.getParentFile() + " for file " + resolvedPath + ".");
                        }
                    }

                    final Lang finalLang = targetLang;
                    final Charset finalEncoding = targetEncoding;
                    Util.writeCompressedFile(resolvedPath, target.compression, output -> writeStatements(output, stmts, finalLang, finalEncoding, report));
                }
            }
        } catch (BurpException e) {
            if (!report.getErrors().contains(e.getError())) {
                report.getErrors().add(e.getError());
            }
        } catch (Exception e) {
            report.getErrors().add(Errors.UnexpectedError(e));
        } finally {
            boolean useColors = color != null ? color : FileHighlight.hasColorSupport();
            System.out.print(PlainTextReportGenerator.generateTextReport(report, useColors));
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
            case "http://www.w3.org/ns/formats/N3" -> Lang.N3;
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

    private static void writeStatements(OutputStream output, List<RdfStatement> statements, Lang lang, Charset encoding, RmlExecutionReport report) {
        if (lang == Lang.NQ || lang == Lang.NT) {
            NQuadsWriter.write(output, statements, encoding);
            if (report != null) {
                report.getStatistics().setGeneratedStatements((long) statements.size());
            }
            return;
        }

        Dataset ds = generateDataset(statements, report);
        if (RDFLanguages.isQuads(lang)) {
            RDFDataMgr.write(output, ds, lang);
        } else {
            RDFDataMgr.write(output, ds.getDefaultModel(), lang);
        }
    }

    private static Dataset generateDataset(List<RdfStatement> statements, RmlExecutionReport report) {
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
        if (report != null) {
            report.getStatistics().setGeneratedStatements(count);
        }

        return ds;
    }
}
