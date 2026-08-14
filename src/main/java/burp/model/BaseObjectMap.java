package burp.model;

public sealed interface BaseObjectMap extends PlanNode, TermGenerator permits ObjectMap, ReferencingObjectMap {
}
