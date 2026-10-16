package burp.parse.loader;

import burp.model.BaseObjectMap;
import burp.model.ObjectMap;
import burp.model.ReferencingObjectMap;
import burp.model.TermGenerator;
import burp.vocabularies.RML;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.Nullable;

/**
 * Resolves polymorphic {@link BaseObjectMap} / {@link TermGenerator} targets to either
 * {@link ReferencingObjectMap} (when {@code rml:parentTriplesMap} is present) or standard {@link ObjectMap}.
 */
public class ObjectMapTypeDecider implements TypeDecider {
    @Override
    public Class<?> decide(Model model, Resource resource, Class<?> targetClass) {
        Decision d = decideType(model, resource, targetClass);
        return d != null ? d.concreteClass() : null;
    }

    @Override
    public @Nullable Decision decideType(Model model, Resource resource, Class<?> targetClass) {
        if (!BaseObjectMap.class.isAssignableFrom(targetClass) && !targetClass.equals(TermGenerator.class)) {
            return null;
        }
        if (resource != null && resource.hasProperty(RML.parentTriplesMap)) {
            return new Decision(ReferencingObjectMap.class, resource.getProperty(RML.parentTriplesMap));
        }
        return new Decision(ObjectMap.class, null);
    }
}
