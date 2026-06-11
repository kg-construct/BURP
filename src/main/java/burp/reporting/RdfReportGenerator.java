package burp.reporting;

import burp.Main;
import burp.vocabularies.PTR;
import burp.vocabularies.RER;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;

public class RdfReportGenerator {

    public static void generateRdfReport(RmlExecutionReport report, String outputFile) {
        Model model = ModelFactory.createDefaultModel();
        Resource reportRdf = model.createResource(RER.RmlExecutionReport);

        Properties props = new Properties();
        try (InputStream propsFile = Main.class.getResourceAsStream("/burp.properties")) {
            if (propsFile != null) {
                props.load(propsFile);
            }
        } catch (Exception ignored) {
        }

        String processorName = props.getProperty("processor.name", null);
        String processorVersion = props.getProperty("processor.version", null);

        if (processorName != null) {
            reportRdf.addProperty(RER.processorName, processorName);
        }
        if (processorVersion != null) {
            reportRdf.addProperty(RER.processorVersion, processorVersion);
        }

        reportRdf.addProperty(
            RER.generatedStatements,
            model.createTypedLiteral(report.getStatistics().getGeneratedStatements())
        );

        if (report.getStatistics().getGeneratedStatementPerTriplesMap() != null) {
            for (var entry : report.getStatistics().getGeneratedStatementPerTriplesMap().entrySet()) {
                Resource bn = model.createResource(RER.GeneratedStatementPerTriplesMap);
                bn.addProperty(RER.triplesMap, model.createResource(entry.getKey().subject));
                bn.addProperty(RER.generatedStatements, model.createTypedLiteral(entry.getValue()));
                reportRdf.addProperty(RER.generatedStatementsPerTriplesMap, bn);
            }
        }

        if (report.getErrors() != null) {
            for (RmlError error : report.getErrors()) {
                addRmlError(model, reportRdf, error);
            }
        }

        Lang lang = RDFLanguages.filenameToLang(outputFile);
        if (lang == null) lang = Lang.NT;

        try (FileOutputStream out = new FileOutputStream(outputFile)) {
            RDFDataMgr.write(out, model, lang);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write RDF report to " + outputFile, e);
        }
    }

    private static void addRmlError(Model model, Resource reportResource, RmlError error) {
        Resource errorResource = model.createResource(error.getErrorType());
        model.add(reportResource, RER.hasError, errorResource);
        errorResource.addProperty(RER.message, error.getMessage());

        if (error.getException() != null) {
            errorResource.addProperty(RER.stackTrace, getStackTraceAsString(error.getException()));
        }

        if (error.getContext() != null) {
            for (var entry : error.getContext().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Resource) {
                    errorResource.addProperty(entry.getKey(), (Resource) value);
                } else {
                    errorResource.addProperty(entry.getKey(), model.createTypedLiteral(value));
                }
            }
        }

        if (error.getOrigin() != null) {
            Origin origin = error.getOrigin();
            if (origin.planNode() != null) {
                errorResource.addProperty(RER.planNode, origin.planNode().toString());
            }

            if (origin.sourceStatements() != null) {
                for (RDFPointer rdfPtr : origin.sourceStatements()) {
                    switch (rdfPtr) {
                        case StatementParts(
                                var stmt, boolean subject, boolean predicate, boolean object
                        ) -> {
                            Resource sp = model.createResource(PTR.StatementPart);
                            sp.addProperty(PTR.statement, model.createStatementTerm(stmt));

                            if (subject) sp.addProperty(PTR.part, PTR.Subject);
                            if (predicate) sp.addProperty(PTR.part, PTR.Predicate);
                            if (object) sp.addProperty(PTR.part, PTR.Object);

                            model.add(errorResource, RER.mappingStatement, sp);
                        }
                        case LiteralPart(var stmt, var range) -> {
                            Resource sp = model.createResource(PTR.StatementPart);
                            sp.addProperty(PTR.statement, model.createStatementTerm(stmt));
                            sp.addProperty(PTR.part, PTR.Object);
                            sp.addProperty(PTR.textRange, createRangeResource(model, range));
                            model.add(errorResource, RER.mappingStatement, sp);
                        }
                        case TextFilePointer tfp -> {
                            Resource tfpRes = model.createResource(PTR.TextFilePointer);
                            if (tfp.path() != null)
                                tfpRes.addProperty(PTR.path, tfp.path().toString());
                            if (tfp.range() != null)
                                tfpRes.addProperty(PTR.range, createRangeResource(model, tfp.range()));
                            model.add(errorResource, RER.mappingStatement, tfpRes);
                        }

                        case null, default -> {
                        }
                    }
                }
            }
        }
    }

    private static Resource createRangeResource(Model model, PointRange range) {
        Resource rangeRes = model.createResource(PTR.Range);

        Resource startPt = model.createResource(PTR.Point);
        startPt.addProperty(PTR.line, model.createTypedLiteral(range.start().getDisplayLine()));
        startPt.addProperty(PTR.column, model.createTypedLiteral(range.start().getDisplayColumn()));
        rangeRes.addProperty(PTR.start, startPt);

        if (range.end() != null) {
            Resource endPt = model.createResource(PTR.Point);
            endPt.addProperty(PTR.line, model.createTypedLiteral(range.end().getDisplayLine()));
            endPt.addProperty(PTR.column, model.createTypedLiteral(range.end().getDisplayColumn()));
            rangeRes.addProperty(PTR.end, endPt);
        }

        return rangeRes;
    }

    private static String getStackTraceAsString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
