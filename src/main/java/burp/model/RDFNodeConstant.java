package burp.model;

import burp.model.rdf.Term;

import java.util.Collections;

public class RDFNodeConstant implements Expression {
    public Term constant;
    private PlanNode parent = null;

    public RDFNodeConstant() {
        this.constant = null;
    }

    public RDFNodeConstant(Term constant) {
        this.constant = constant;
    }

    @Override
    public PlanNode getParent() {
        return parent;
    }

    @Override
    public void setParent(PlanNode parent) {
        this.parent = parent;
    }

    @Override
    public Iterable<PlanNode> children() {
        return Collections.emptyList();
    }

    @Override
    public Iterable<PlanNode> dependencies() {
        return Collections.emptyList();
    }
}