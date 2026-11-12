package burp.parse.loader;

import burp.model.Expression;
import burp.model.RDFNodeConstant;
import burp.model.RawReference;
import burp.model.Template;
import burp.model.fnml.FunctionExecution;
import burp.vocabularies.BURP;
import burp.vocabularies.RML;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
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

    private static boolean isHole(@Nullable Statement stmt) {
        if (stmt == null) return false;
        RDFNode obj = stmt.getObject();
        return obj.isURIResource() && BURP.hole.getURI().equals(obj.asResource().getURI());
    }

    @Override
    public @Nullable Decision decideType(Model model, Resource resource, Class<?> targetClass) {
        if (!Expression.class.isAssignableFrom(targetClass) || resource == null) {
            return null;
        }
        Statement feStmt = resource.getProperty(RML.functionExecution);
        if (feStmt != null && !isHole(feStmt)) {
            return new Decision(FunctionExecution.class, feStmt);
        }
        Statement tmplStmt = resource.getProperty(RML.template);
        if (tmplStmt != null && !isHole(tmplStmt)) {
            return new Decision(Template.class, tmplStmt);
        }
        Statement refStmt = resource.getProperty(RML.reference);
        if (refStmt != null && !isHole(refStmt)) {
            return new Decision(RawReference.class, refStmt);
        }
        Statement constStmt = resource.getProperty(RML.constant);
        if (constStmt != null && !isHole(constStmt)) {
            return new Decision(RDFNodeConstant.class, constStmt);
        }
        return null;
    }
}
