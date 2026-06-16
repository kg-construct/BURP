package burp.model;

import org.apache.jena.rdf.model.Resource;

public interface FormulationIterable extends PlanNode {
    Resource getReferenceFormulation();
    void setReferenceFormulation(Resource referenceFormulation);
}