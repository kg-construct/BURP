package burp.model;

import java.util.ArrayList;
import java.util.List;

public class JoinCondition implements PlanNode, ReferenceHolder {
    public ConcreteExpressionMap parentMap;
    public ConcreteExpressionMap childMap;

    private PlanNode parent = null;

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
        List<PlanNode> list = new ArrayList<>();
        if (parentMap != null) list.add(parentMap);
        if (childMap != null) list.add(childMap);
        return list;
    }

    @Override
    public Iterable<PlanNode> dependencies() {
        return children();
    }

    @Override
    public void compileReferences() {
        ParentJoinReferenceScope parentScope = ancestor(ParentJoinReferenceScope.class);
        if (parentScope != null && parentMap != null) {
            for (RawReference ref : parentMap.descendants(RawReference.class).toList()) {
                if (ref.getReference() != null && ref.getCompiledReference() == null) {
                    ref.setCompiledReference(parentScope.buildParentJoinReference(ref.getReference(), ref.getOrigin()));
                }
            }
        }
        
        LocalReferenceScope localScope = ancestor(LocalReferenceScope.class);
        if (localScope != null && childMap != null) {
            for (RawReference ref : childMap.descendants(RawReference.class).toList()) {
                if (ref.getReference() != null && ref.getCompiledReference() == null) {
                    ref.setCompiledReference(localScope.buildLocalReference(ref.getReference(), ref.getOrigin()));
                }
            }
        }
    }
}