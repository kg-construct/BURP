package burp.reporting;

import burp.model.PlanNode;
import burp.vocabularies.RER;
import org.apache.jena.ontology.OntClass;

import java.nio.file.Path;
import java.util.function.Consumer;

public class Errors {
    
    public static String fileLocationString(Path file, PointRange location) {
        String locationStr = "";
        if (location != null) {
            String start = location.getStart().getDisplayLine() + ":" + location.getStart().getDisplayColumn();
            String end = null;
            if (location.getEnd() != null) {
                end = location.getEnd().getDisplayLine() + ":" + location.getEnd().getDisplayColumn();
            }
            locationStr = (end != null) ? start + "-" + end : start;
        }

        return !locationStr.isEmpty() ? file.toString() + ":" + locationStr : file.toString();
    }

    public static RmlError rmlError(OntClass errorType, Consumer<RmlErrorBuilder> init) {
        RmlErrorBuilder builder = new RmlErrorBuilder(errorType);
        init.accept(builder);
        return builder.build();
    }

    public static RmlError SourceAccessError(String message, Origin info, Exception ex) {
        return new RmlError(message, info, RER.SourceAccessError, ex);
    }

    public static RmlError InvalidRDF(String message, Origin info, OntClass type) {
        return new RmlError(message, info, type);
    }

    public static RmlError ReferenceFormulationSyntaxError(String message, Origin info) {
        return new RmlError(message, info, RER.ReferenceFormulationSyntaxError);
    }

    public static RmlError ReferenceFormulationExecutionError(String message, PlanNode planNode) {
        return new RmlError(message, new Origin(planNode, null), RER.ReferenceFormulationExecutionError);
    }

    public static RmlError RDFMappingSyntaxError(String message, Origin info) {
        return new RmlError(message, info, RER.RDFMappingSyntaxError);
    }

    public static RmlError UnsupportedMapping(String message, Origin info) {
        return new RmlError(message, info, RER.UnsupportedMapping);
    }

    public static RmlError NoTriplesMap() {
        return new RmlError("No triples map (with rml:logicalSource) found in mapping.", new Origin(), RER.NoTriplesMap);
    }

    public static RmlError UnexpectedError(Exception ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "Unexpected error look at stack trace.";
        return new RmlError(msg, null, RER.Error, ex);
    }
}
