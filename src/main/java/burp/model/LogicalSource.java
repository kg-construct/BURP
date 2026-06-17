package burp.model;

public abstract class LogicalSource extends AbstractLogicalSource {
    @Override
    public Iterable<PlanNode> dependencies() {
        return children();
    }
}