package burp.model;

import burp.parse.turtleprov.ProvTurtleVisitor;
import burp.reporting.Origin;
import burp.reporting.PointRange;

import java.util.Collections;
import java.util.List;

public class RawReference extends Reference implements ReferenceHolder {
    private Reference compiledReference = null;

    public RawReference(String reference, Origin origin) {
        super(reference, origin);
    }

    public Reference getCompiledReference() {
        return compiledReference;
    }

    public void setCompiledReference(Reference compiledReference) {
        this.compiledReference = compiledReference;
    }

    @Override
    public List<PointRange> nodeRanges() {
        Object pointers = origin.sourceStatements();
        if (pointers == null) return Collections.emptyList();
        return ProvTurtleVisitor.retrieveTurtleLocation(pointers);
    }

    @Override
    public List<Object> getValues(Iteration i) {
        return compiledReference.getValues(i);
    }

    @Override
    public void compileReferences() {
        if (compiledReference == null && reference != null) {
            LocalReferenceScope scope = ancestor(LocalReferenceScope.class);
            System.out.println("Compiling RawReference: " + reference + ", found scope: " + (scope != null ? scope.getClass().getSimpleName() : "null"));
            if (scope != null) {
                compiledReference = scope.buildLocalReference(reference, origin);
                System.out.println("   compiled to: " + (compiledReference != null ? compiledReference.getClass().getSimpleName() : "null"));
            }
        }
    }

    @Override
    public List<String> getStrings(Iteration i) {
        return compiledReference.getStrings(i);
    }
}
