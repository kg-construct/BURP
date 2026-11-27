package burp.parse.loader.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field of type {@link burp.reporting.RDFPointer}, {@link burp.reporting.StatementParts},
 * or {@link burp.reporting.Origin} to receive the RDF statement provenance of the predicate part
 * of the enclosing mapped property (for example, {@code rml:child} or {@code rml:parent}).
 *
 * <p>This allows child AST nodes (such as {@link burp.model.ChildMap} and {@link burp.model.ParentMap})
 * to know their predicate token location in the mapping document directly, enabling editor inlay hint
 * providers to place source hints precisely without manual AST / graph pointer traversal.
 * <p>
 * Other origin metadata is obtainable via PlanNode.getOrigin(field).
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface OriginPredicate {
    String propertyName() default "";
}
