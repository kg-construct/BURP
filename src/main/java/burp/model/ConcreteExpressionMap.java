package burp.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 
 * A ConcreteExpressionMap is a concrete implementation of the abstract class
 * ExpressionMap for use in join conditions and logical views
 *
 */
public class ConcreteExpressionMap extends ExpressionMap {

	public List<Object> generateValues(Iteration i) {

		if(expression instanceof RDFNodeConstant)
			return Collections.singletonList(((RDFNodeConstant) expression).constant);
		
		if(expression instanceof Template)
            return new ArrayList<>(((Template) expression).values(i));
		
		if(expression instanceof Reference)
			return ((Reference) expression).values(i);

        if(expression instanceof FunctionExecution)
            return ((FunctionExecution) expression).values(i, null);
		
		throw new RuntimeException("Error generating values.");
	}

}