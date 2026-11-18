package burp.parse.loader.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a target model class is unwrapped through an intermediate RDF predicate
 * before its fields are populated.
 *
 * <p>When {@link burp.parse.loader.ReflectiveRdfObjectLoader} maps a resource to a class annotated with
 * {@code @RdfWrapped(predicateUri)}:
 * <ol>
 *   <li>The loader checks whether the candidate resource possesses the specified {@code value()} predicate.</li>
 *   <li>If present, it resolves the statement's object resource and maps the target class from that unwrapped resource.</li>
 *   <li>If absent, the loader treats the candidate resource as not matching the target class and returns {@code null},
 *       preventing empty instantiation during polymorphic resolution.</li>
 * </ol>
 *
 * Used, for example, by {@code burp.model.fnml.FunctionExecution} that needs to navigate rml:functionExecution first.
 * </p>
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RdfWrapped {
    /**
     * The URI of the RDF predicate that wraps the target object resource.
     */
    String value();
}
