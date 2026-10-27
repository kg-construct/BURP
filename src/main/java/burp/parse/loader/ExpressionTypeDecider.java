package burp.parse.loader;

import burp.model.Expression;
import burp.model.RDFNodeConstant;
import burp.model.RawReference;
import burp.model.Template;
import burp.model.fnml.FunctionExecution;
import burp.vocabularies.RML;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.Nullable;

/**
 * Resolves polymorphic {@link Expression} types ({@link FunctionExecution}, {@link Template},
 * {@link RawReference}, {@link RDFNodeConstant}) by inspecting defining properties
 * ({@code rml:functionExecution}, {@code rml:function}, {@code rml:template}, {@code rml:reference}, {@code rml:constant})
 * or explicit {@code rdf:type}.
 */
public class ExpressionTypeDecider implements TypeDecider {
    @Override
    public Class<?> decide(Model model, Resource resource, Class<?> targetClass) {
        Decision decision = decideType(model, resource, targetClass);
        return decision != null ? decision.concreteClass() : null;
    }

    @Override
    public @Nullable Decision decideType(Model model, Resource resource, Class<?> targetClass) {
        if (!Expression.class.isAssignableFrom(targetClass) || resource == null) {
            return null;
        }
        if (resource.hasProperty(RML.functionExecution)) {
            return new Decision(FunctionExecution.class, resource.getProperty(RML.functionExecution));
        }
        if (resource.hasProperty(RML.template)) {
            return new Decision(Template.class, resource.getProperty(RML.template));
        }
        if (resource.hasProperty(RML.reference)) {
            return new Decision(RawReference.class, resource.getProperty(RML.reference));
        }
        if (resource.hasProperty(RML.constant)) {
            return new Decision(RDFNodeConstant.class, resource.getProperty(RML.constant));
        }
        return null;
    }
}
