package burp.model.lv;

import burp.model.*;
import burp.model.key.AstKey;
import burp.parse.loader.annotations.RdfId;
import burp.parse.loader.annotations.RdfProperty;
import burp.reporting.Origin;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class Field extends AbstractPlanNode implements ContainsFields, FieldParent, LocalReferenceScope {

    @RdfId
    public Resource subject;

    @Override
    public @NonNull Resource getIri() {
        return (subject);
    }

    @RdfProperty(Rml.fieldName)
    public String fieldName;

    @RdfProperty(Rml.field)
    public List<Field> fields = new ArrayList<>();

    public FieldParent parentField;

    public static List<LogicalIteration> expand(List<LogicalIteration> list, List<ExpressionField> expressionFields, List<IterableField> iterableFields) {
        List<LogicalIteration> result = new ArrayList<>(list);

        if (expressionFields != null && !expressionFields.isEmpty()) {
            for (ExpressionField expressionField : expressionFields) {
                List<LogicalIteration> nlist = new ArrayList<>();
                for (LogicalIteration li : result) {
                    nlist.addAll(expressionField.enrich(li));
                }
                result = nlist;
            }
        }

        if (iterableFields != null && !iterableFields.isEmpty()) {
            List<LogicalIteration> nlist = new ArrayList<>();
            for (LogicalIteration li : result) {
                for (IterableField iterableField : iterableFields) {
                    nlist.addAll(iterableField.enrich(li));
                }
            }
            result = nlist;
        }

        return result;
    }

    private @Nullable String absoluteFieldName = null;

    public void wire(FieldParent parent) {
        this.parentField = parent;
        if (parent instanceof AbstractLogicalSource) {
            this.absoluteFieldName = fieldName;
        } else if (parent instanceof Field f) {
            this.absoluteFieldName = f.getAbsoluteFieldName() + "." + fieldName;
        } else if (parent != null) {
            this.absoluteFieldName = parent.getAbsoluteFieldName() + "." + fieldName;
        }
        for (Field child : fields) {
            child.wire(this);
        }
    }

    @Override
    public String getAbsoluteFieldName() {
        if (absoluteFieldName != null) {
            return absoluteFieldName;
        }
        if (parentField instanceof AbstractLogicalSource)
            return fieldName;

        if (parentField instanceof Field pf)
            return pf.getAbsoluteFieldName() + "." + fieldName;

        return fieldName;
    }


    @Override
    public Iterable<PlanNode> children() {
        List<PlanNode> children = new ArrayList<>();
        children.addAll(getExpressionFields());
        children.addAll(getIterableFields());
        return children;
    }

    @Override
    public List<IterableField> getIterableFields() {
        List<IterableField> list = new ArrayList<>();
        for (Field f : fields) {
            if (f instanceof IterableField inf) {
                list.add(inf);
            }
        }
        return list;
    }

    @Override
    public List<ExpressionField> getExpressionFields() {
        List<ExpressionField> list = new ArrayList<>();
        for (Field f : fields) {
            if (f instanceof ExpressionField ef) {
                list.add(ef);
            }
        }
        return list;
    }

    @Override
    public @Nullable AbstractLogicalSource getLogicalSource() {
        LocalReferenceScope ancestorScope = ancestor(LocalReferenceScope.class);
        return ancestorScope != null ? ancestorScope.getLogicalSource() : null;
    }

    @Override
    public Reference buildLocalReference(String reference, Origin origin) {
        LocalReferenceScope ancestorScope = ancestor(LocalReferenceScope.class);
        if (ancestorScope != null) {
            return ancestorScope.buildLocalReference(reference, origin);
        }
        AbstractLogicalSource ls = getLogicalSource();
        if (ls != null) {
            return ls.buildExportedReference(reference, origin);
        }
        throw new IllegalArgumentException("Cannot resolve reference " + reference + " in " + this);
    }

    @Override
    public AstKey computeAstKey() {
        List<AstKey> childKeys = new ArrayList<>();
        for (PlanNode child : children()) {
            if (child != null) {
                childKeys.add(child.getAstKey());
            }
        }
        return new AstKey.FieldKey(fieldName != null ? fieldName : "", childKeys);
    }
}
