package burp.model;

import burp.parse.loader.annotations.RdfType;
import burp.vocabularies.Rml;

/**
 * A ConcreteExpressionMap is a concrete implementation of the abstract class
 * ExpressionMap for use in join conditions and logical views
 */
@RdfType(Rml.ExpressionMap)
public class ConcreteExpressionMap extends ExpressionMap {
}