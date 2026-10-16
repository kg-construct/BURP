package burp.parse.loader.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a shortcut property that expands to an intermediate node with targetProperty.
 * E.g., @RdfShortcutProperty(shortcutUri = RML.predicate, targetProperty = RML.constant)
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface RdfShortcutProperty {
    String shortcutUri();
    String targetProperty();
}
