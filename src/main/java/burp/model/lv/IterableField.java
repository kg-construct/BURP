package burp.model.lv;

import burp.ls.LogicalSourceFactory;
import burp.model.*;
import burp.model.context.EvaluationContext;
import burp.model.key.AstKey;
import burp.parse.loader.annotations.RdfProperty;
import burp.parse.loader.annotations.RdfType;
import burp.reporting.Origin;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RdfType(Rml.IterableField)
public class IterableField extends Field implements FormulationIterable {

    @RdfProperty(Rml.iterator)
    public String iterator;

    @RdfProperty(Rml.referenceFormulation)
    public Resource declaredReferenceFormulation;

    @Override
    public Resource getReferenceFormulation() {
        return declaredReferenceFormulation != null ? declaredReferenceFormulation : getAncestorReferenceFormulation();
    }

    @Override
    public Reference buildLocalReference(String reference, Origin origin) {
        if (declaredReferenceFormulation == null) {
            LocalReferenceScope ancestorReferenceScope = ancestor(LocalReferenceScope.class);
            if (ancestorReferenceScope == null) {
                throw new IllegalArgumentException("No ancestor reference formulation scope in " + this);
            }
            return ancestorReferenceScope.buildLocalReference(reference, origin);
        }

        return LogicalSourceFactory.buildReference(
                declaredReferenceFormulation,
                reference,
                origin,
                emitOrigin("declaredReferenceFormulation")
        );
    }

    public List<Iteration> extractSubIterations(@Nullable Iteration parentIteration) {
        if (parentIteration == null) {
            return Collections.emptyList();
        }
        return extractSubIterations(parentIteration.asString());
    }

    public List<Iteration> extractSubIterations(@Nullable String iterationContent) {
        if (iterationContent == null) {
            return Collections.emptyList();
        }
        return LogicalSourceFactory.changeIterator(
                iterationContent,
                getReferenceFormulation(),
                iterator,
                emitOrigin("declaredReferenceFormulation")
        );
    }

    public List<LogicalIteration> enrich(LogicalIteration underlying) {
        var list = new ArrayList<LogicalIteration>();

        // The iterator has changed
        // We take the iterator from the parent
        var iterationContent = underlying.getIterationString(parentField.getAbsoluteFieldName());
        var changedIterator = extractSubIterations(iterationContent);

        for (int index = 0; index < changedIterator.size(); index++) {
            var iteration = changedIterator.get(index);
            var e = underlying.copy();
            e.put(getAbsoluteFieldName() + ".#", index);
            e.put(getAbsoluteFieldName(), iteration);
            list.add(e);
        }

        return expand(list, getExpressionFields(), getIterableFields());
    }

    @Override
    public List<RmlEvaluationResult> evaluateNode(@Nullable Iteration iter, EvaluationContext context) {
        if (iter == null) {
            return Collections.emptyList();
        }
        return List.of(new RawValue(iter.asString()));
    }

    /**
     * Resolves the effective reference formulation inherited from the ancestor hierarchy.
     *
     * @return the {@link Resource} representing the reference formulation of the nearest enclosing
     *         {@link IterableField} (if declared) or the root {@link AbstractLogicalSource}.
     * @throws IllegalStateException if called on an unattached field whose parent hierarchy has not been wired.
     */
    private Resource getAncestorReferenceFormulation() {
        return getReferenceFormulation(parentField);
    }

    private static Resource getReferenceFormulation(@Nullable FieldParent parent) {
        return switch (parent) {
            case IterableField inf when inf.declaredReferenceFormulation != null -> inf.declaredReferenceFormulation;
            case Field f -> getReferenceFormulation(f.parentField);
            case AbstractLogicalSource als -> als.getReferenceFormulation();
            case null, default -> throw new IllegalStateException("Field is not attached to an ancestor LogicalSource or IterableField with a reference formulation: " + parent);
        };
    }

    @Override
    public AstKey computeAstKey() {
        List<AstKey> childKeys = new ArrayList<>();
        for (PlanNode child : children()) {
            if (child != null) {
                childKeys.add(child.getAstKey());
            }
        }
        return new AstKey.IterableFieldKey(
                fieldName != null ? fieldName : "",
                iterator,
                declaredReferenceFormulation != null ? declaredReferenceFormulation.getURI() : null,
                childKeys
        );
    }
}
