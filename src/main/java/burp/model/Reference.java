package burp.model;

import burp.reporting.Origin;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class Reference implements Expression {
    protected final String reference;
    protected Origin origin;
    protected PlanNode parent = null;

    public Reference(String reference, Origin origin) {
        this.reference = reference;
        this.origin = origin;
    }

    public String getReference() {
        return reference;
    }

    public Origin getOrigin() {
        return origin;
    }

    public void setOrigin(Origin origin) {
        this.origin = origin;
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

    public List<Object> values(Iteration i) {
        return getValues(i);
    }

    public abstract List<Object> getValues(Iteration i);

    public List<String> getStrings(Iteration i) {
        return getValues(i).stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toList());
    }
}