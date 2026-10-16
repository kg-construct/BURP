package burp.model;

import burp.parse.loader.annotations.OriginPredicate;
import burp.parse.loader.annotations.RdfType;
import burp.reporting.Origin;
import burp.reporting.RDFPointer;
import burp.vocabularies.Rml;
import org.jspecify.annotations.Nullable;

/**
 * A ParentMap is a concrete implementation of ExpressionMap
 * representing parent expressions in join conditions (rml:parentMap / rml:parent).
 * It acts as a LocalReferenceScope by delegating to the enclosing ParentJoinReferenceScope.
 */
@RdfType(Rml.ParentMap)
public class ParentMap extends ExpressionMap implements LocalReferenceScope {

    @OriginPredicate
    public @Nullable RDFPointer predicatePointer;

    @Override
    public Reference buildLocalReference(String reference, Origin origin) {
        ParentJoinReferenceScope parentScope = ancestor(ParentJoinReferenceScope.class);
        return parentScope != null ? parentScope.buildParentJoinReference(reference, origin) : null;
    }

    @Override
    public @Nullable AbstractLogicalSource getLogicalSource() {
        ParentJoinReferenceScope parentScope = ancestor(ParentJoinReferenceScope.class);
        return parentScope != null ? parentScope.getParentLogicalSource() : null;
    }
}

