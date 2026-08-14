package burp.reporting;

import burp.model.PlanNode;
import burp.vocabularies.RER;

import java.nio.file.Path;

public class Errors {
    
    public static String fileLocationString(Path file, PointRange location) {
        String locationStr = "";
        if (location != null) {
            String start = location.start().getDisplayLine() + ":" + location.start().getDisplayColumn();
            String end = null;
            if (location.end() != null) {
                end = location.end().getDisplayLine() + ":" + location.end().getDisplayColumn();
            }
            locationStr = (end != null) ? start + "-" + end : start;
        }

        return !locationStr.isEmpty() ? file.toString() + ":" + locationStr : file.toString();
    }

    public static RmlError ReferenceFormulationSyntaxError(String message, Origin info) {
        return new RmlError(message, info, RER.ReferenceFormulationSyntaxError);
    }

    public static RmlError ReferenceFormulationExecutionError(String message, PlanNode planNode) {
        return new RmlError(message, new Origin(planNode, null), RER.ReferenceFormulationExecutionError);
    }


    public static RmlError NoTriplesMap() {
        return new RmlError("No triples map (with rml:logicalSource) found in mapping.", new Origin(), RER.NoTriplesMap);
    }

    public static RmlError UnexpectedError(Exception ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "Unexpected error look at stack trace.";
        return new RmlError(msg, null, RER.Error, ex);
    }
}
