package burp.parse.loader.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps a field directly from the current RDF resource rather than from a child property.
 * Optionally specifies candidate concrete classes to attempt in order.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface RdfMappedFrom {
    Class<?>[] value() default {};
}
