package burp.model;

import burp.parse.loader.annotations.RdfType;
import burp.vocabularies.Rml;

/**
 * A Condition is a specialized ExpressionMap that evaluates a boolean condition in RML-FNML.
 */
@RdfType(Rml.ExpressionMap)
public class Condition extends ExpressionMap {
}