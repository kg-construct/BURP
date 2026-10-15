package burp.model;

import burp.parse.loader.annotations.OriginPredicate;
import burp.parse.loader.annotations.RdfType;
import burp.reporting.Origin;
import burp.reporting.RDFPointer;
import burp.vocabularies.Rml;
import org.jspecify.annotations.Nullable;

/**
 * A ChildMap is a concrete implementation of ExpressionMap
 * representing child expressions in join conditions (rml:childMap / rml:child).
 * It acts as a LocalReferenceScope by delegating to the enclosing LocalReferenceScope above JoinCondition.
 */
@RdfType(Rml.ChildMap)
public class ChildMap extends ExpressionMap implements LocalReferenceScope {

    @OriginPredicate
    public @Nullable RDFPointer predicatePointer;

    private @Nullable LocalReferenceScope enclosingScope() {
        return getParent() != null ? getParent().ancestor(LocalReferenceScope.class) : null;
    }

    @Override
    public Reference buildLocalReference(String reference, Origin origin) {
        ParentJoinReferenceScope joinScope = ancestor(ParentJoinReferenceScope.class);
        if (joinScope != null) {
            return joinScope.buildChildJoinReference(reference, origin);
        }
        LocalReferenceScope scope = enclosingScope();
        return scope != null ? scope.buildLocalReference(reference, origin) : null;
    }

    @Override
    public @Nullable AbstractLogicalSource getLogicalSource() {
        ParentJoinReferenceScope joinScope = ancestor(ParentJoinReferenceScope.class);
        if (joinScope != null) {
            return joinScope.getChildLogicalSource();
        }
        LocalReferenceScope scope = enclosingScope();
        return scope != null ? scope.getLogicalSource() : null;
    }
}
