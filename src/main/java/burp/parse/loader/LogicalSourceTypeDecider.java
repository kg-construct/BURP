package burp.parse.loader;

import burp.model.AbstractLogicalSource;
import burp.model.LogicalSource;
import burp.model.lv.LogicalView;
import burp.vocabularies.RML;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.Nullable;

/**
 * Resolves polymorphic {@link AbstractLogicalSource} targets to either {@link LogicalView}
 * (when {@code rml:viewOn} is present) or standard {@link LogicalSource}.
 */
public class LogicalSourceTypeDecider implements TypeDecider {
    @Override
    public Class<?> decide(Model model, Resource resource, Class<?> targetClass) {
        Decision d = decideType(model, resource, targetClass);
        return d != null ? d.concreteClass() : null;
    }

    @Override
    public @Nullable Decision decideType(Model model, Resource resource, Class<?> targetClass) {
        if (!AbstractLogicalSource.class.isAssignableFrom(targetClass)) {
            return null;
        }
        if (resource != null && resource.hasProperty(RML.viewOn)) {
            return new Decision(LogicalView.class, resource.getProperty(RML.viewOn));
        }
        if (resource != null && resource.hasProperty(RML.source)) {
            return new Decision(LogicalSource.class, resource.getProperty(RML.source));
        }
        return new Decision(LogicalSource.class, null);
    }
}
