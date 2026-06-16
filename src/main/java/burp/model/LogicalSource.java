package burp.model;

import org.apache.jena.rdf.model.Resource;

import java.util.Collections;

public abstract class LogicalSource extends AbstractLogicalSource {
    public Resource referenceFormulation;

    @Override
    public Iterable<PlanNode> children() {
        return Collections.emptyList();
    }

    @Override
    public Iterable<PlanNode> dependencies() {
        return children();
    }

    @Override
    public Resource getReferenceFormulation() {
        return referenceFormulation;
    }

    @Override
    public void setReferenceFormulation(Resource referenceFormulation) {
        this.referenceFormulation = referenceFormulation;
    }
}