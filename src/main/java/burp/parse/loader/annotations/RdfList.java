package burp.parse.loader.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a collection field (List or Set) to be populated by traversing an RDF linked list (rdf:List).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface RdfList {
    /**
     * The URI of the property pointing to the head of the rdf:List.
     */
    String value();
}
