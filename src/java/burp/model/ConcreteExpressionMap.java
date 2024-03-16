package burp.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 
 * A ConcreteExpressionMap is a concrete implementation of the abstract class
 * ExpressionMap for use in join conditions
 *
 */
public class ConcreteExpressionMap extends ExpressionMap {

	public List<String> generateValues(Iteration i) {
		List<String> l = new ArrayList<String>();
		
		if(expression instanceof RDFNodeConstant) {
			// It is assumed to be a string, otherwise the shapes
			// Would have caught the error.
			l.add(((RDFNodeConstant) expression).constant.toString());
			return l;
		}
		
		if(expression instanceof Template) {
			l.addAll(((Template) expression).values(i));
			return l;
		}
		
		if(expression instanceof Reference) {
			List<Object> values = ((Reference) expression).values(i);
			for(Object o : values)
				l.add(o.toString());
			return l;
		}
		
		throw new RuntimeException("Error generating language string.");
	}

}