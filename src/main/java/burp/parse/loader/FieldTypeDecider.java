package burp.parse.loader;

import burp.model.lv.ExpressionField;
import burp.model.lv.Field;
import burp.model.lv.IterableField;
import burp.vocabularies.RML;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether a Field resource in a LogicalView is an {@link IterableField} or an {@link ExpressionField}.
 * Respects explicit {@code rdf:type} first, and falls back to inspecting properties ({@code rml:iterator}, {@code rml:referenceFormulation}).
 */
public class FieldTypeDecider implements TypeDecider {

    @Override
    public Class<?> decide(Model model, Resource resource, Class<?> targetClass) {
        Decision d = decideType(model, resource, targetClass);
        return d != null ? d.concreteClass() : null;
    }

    @Override
    public @Nullable Decision decideType(Model model, Resource resource, Class<?> targetClass) {
        if (!Field.class.isAssignableFrom(targetClass) || resource == null) return null;

        // Either explicitly typed or has (rml:iterator or rml:referenceFormulation).
        for (Statement stmt : resource.listProperties(RDF.type).toList()) {
            if (stmt.getObject().isResource()) {
                Resource uri = stmt.getObject().asResource();
                if (uri.equals(RML.IterableField)) {
                    return new Decision(IterableField.class, stmt);
                }
                if (uri.equals(RML.ExpressionField)) {
                    return new Decision(ExpressionField.class, stmt);
                }
            }
        }

        if (resource.hasProperty(RML.iterator)) {
            return new Decision(IterableField.class, resource.getProperty(RML.iterator));
        }
        if (resource.hasProperty(RML.referenceFormulation)) {
            return new Decision(IterableField.class, resource.getProperty(RML.referenceFormulation));
        }
        return new Decision(ExpressionField.class, null);
    }
}
