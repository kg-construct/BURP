package burp.model;


import burp.reporting.BurpException;

import java.util.HashSet;
import java.util.Set;

public abstract class AbstractLogicalSource implements FormulationIterable, FieldParent, ExportedReferenceScope, LogicalTargetScope {

    public Set<Object> nulls = new HashSet<>();
    public Set<Object> getNulls() { return nulls; }
    private Set<LogicalTarget> logicalTargets = new HashSet<>();
    private PlanNode parent = null;

    @Override
    public Set<LogicalTarget> getLogicalTargets() {
        return logicalTargets;
    }

    @Override
    public PlanNode getParent() {
        return parent;
    }

    @Override
    public void setParent(PlanNode parent) {
        this.parent = parent;
    }

    public abstract Iterable<Iteration> iterator() throws BurpException;

    @Override
    public String getAbsoluteFieldName() {
        return "<i>";
    }
}