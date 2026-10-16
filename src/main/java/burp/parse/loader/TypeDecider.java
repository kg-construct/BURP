package burp.parse.loader;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.jspecify.annotations.Nullable;

/**
 * Strategy interface for resolving concrete implementation classes of polymorphic target types
 * (such as {@code Expression}, {@code Field}, {@code LogicalSource}, or {@code BaseObjectMap})
 * when loading from RDF graphs.
 */
public interface TypeDecider {

    /**
     * Represents the outcome of a type decision, pairing the resolved concrete class with
     * the RDF statement that triggered the decision (for provenance and origin tracking).
     *
     * @param concreteClass     the resolved concrete implementation class
     * @param definingStatement the RDF statement that prompted the resolution, if any
     */
    record Decision(Class<?> concreteClass, @Nullable Statement definingStatement) {}

    /**
     * Resolves the concrete implementation class for the given RDF resource and target type.
     *
     * @param model       the Jena RDF model containing the triples
     * @param resource    the RDF resource being mapped
     * @param targetClass the requested interface or base target class
     * @return the resolved concrete class, or {@code null} if this decider cannot resolve the type
     */
    Class<?> decide(Model model, Resource resource, Class<?> targetClass);

    /**
     * Resolves the concrete implementation class along with the RDF statement that triggered
     * the decision.
     *
     * @param model       the Jena RDF model containing the triples
     * @param resource    the RDF resource being mapped
     * @param targetClass the requested interface or base target class
     * @return a {@link Decision} containing the concrete class and defining statement, or {@code null}
     */
    default @Nullable Decision decideType(Model model, Resource resource, Class<?> targetClass) {
        Class<?> clazz = decide(model, resource, targetClass);
        return clazz != null ? new Decision(clazz, null) : null;
    }
}
