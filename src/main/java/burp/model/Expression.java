package burp.model;

public interface Expression extends PlanNode {
    @Override
    PlanNode getParent();

    @Override
    void setParent(PlanNode parent);
}
