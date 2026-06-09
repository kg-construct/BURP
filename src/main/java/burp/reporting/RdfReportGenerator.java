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
                    if (rdfPtr instanceof StatementParts stmtPtr) {
                        Resource sp = model.createResource(PTR.StatementPart);
                        sp.addProperty(PTR.statement, model.createStatementTerm(stmtPtr.getStmt()));
                        
                        if (stmtPtr.isSubject()) sp.addProperty(PTR.part, PTR.Subject);
                        if (stmtPtr.isPredicate()) sp.addProperty(PTR.part, PTR.Predicate);
                        if (stmtPtr.isObject()) sp.addProperty(PTR.part, PTR.Object);
                        
                        model.add(errorResource, RER.mappingStatement, sp);
                    }
                    //TODO TextFilePointer and LiteralPart
                }
            }
        }
    }

    private static String getStackTraceAsString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
