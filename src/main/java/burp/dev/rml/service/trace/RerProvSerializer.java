package burp.dev.rml.service.trace;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.jspecify.annotations.NullMarked;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

/**
 * Serializes execution trace nodes and value provenance into standard W3C PROV-O
 * and RML Execution Report (RER) JSON-LD.
 */
@NullMarked
public final class RerProvSerializer {

    public static final String RER_NS = "http://w3id.org/rml/reports/";
    public static final String PROV_NS = "http://www.w3.org/ns/prov#";

    private RerProvSerializer() {}

    /**
     * Serializes trace nodes and trace values into a JSON-LD graph.
     *
     * @param traceNodes     the traced AST nodes
     * @param allTraceValues map of all generated/consumed trace values
     * @return serialized JSON-LD graph string
     */
    public static String serialize(List<TraceNode> traceNodes, Map<String, TraceValue> allTraceValues) {
        Model graph = ModelFactory.createDefaultModel();

        Resource traceNodeClass = graph.createResource(RER_NS + "TraceNode");
        Resource traceValueClass = graph.createResource(RER_NS + "TraceValue");

        Property generatedOutput = graph.createProperty(RER_NS + "generatedOutput");
        Property consumedInput = graph.createProperty(RER_NS + "consumedInput");
        Property producedBy = graph.createProperty(RER_NS + "producedBy");
        Property valueText = graph.createProperty(RER_NS + "valueText");
        Property iterationIdProp = graph.createProperty(RER_NS + "iterationId");
        Property dependsOn = graph.createProperty(RER_NS + "dependsOn");
        Property usedBy = graph.createProperty(RER_NS + "usedBy");
        Property wasDerivedFrom = graph.createProperty(PROV_NS + "wasDerivedFrom");

        for (TraceNode traceNode : traceNodes) {
            Resource nodeRes = graph.createResource(RER_NS + "trace/" + traceNode.nodeId());
            nodeRes.addProperty(RDF.type, traceNodeClass);

            for (TraceValue inputVal : traceNode.inputs()) {
                Resource valRes = graph.createResource(RER_NS + "val/" + inputVal.valueId());
                nodeRes.addProperty(consumedInput, valRes);
            }

            for (TraceValue outputVal : traceNode.outputs()) {
                Resource valRes = graph.createResource(RER_NS + "val/" + outputVal.valueId());
                nodeRes.addProperty(generatedOutput, valRes);
            }

            for (String depId : traceNode.dependencyIds()) {
                Resource depRes = graph.createResource(RER_NS + "trace/" + depId);
                nodeRes.addProperty(dependsOn, depRes);
            }

            for (String futureId : traceNode.futureIds()) {
                Resource futureRes = graph.createResource(RER_NS + "trace/" + futureId);
                nodeRes.addProperty(usedBy, futureRes);
            }
        }

        for (TraceValue tv : allTraceValues.values()) {
            Resource valRes = graph.createResource(RER_NS + "val/" + tv.valueId());
            valRes.addProperty(RDF.type, traceValueClass);
            valRes.addProperty(valueText, tv.text());
            valRes.addProperty(iterationIdProp, tv.iterationId());
            Resource prodNodeRes = graph.createResource(RER_NS + "trace/" + tv.producerNodeId());
            valRes.addProperty(producedBy, prodNodeRes);
            for (String parentValId : tv.derivedFromValueIds()) {
                Resource parentValRes = graph.createResource(RER_NS + "val/" + parentValId);
                valRes.addProperty(wasDerivedFrom, parentValRes);
            }
        }

        StringWriter writer = new StringWriter();
        graph.write(writer, "JSON-LD");
        return writer.toString();
    }
}
