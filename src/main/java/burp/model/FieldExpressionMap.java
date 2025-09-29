package burp.model;

import java.util.ArrayList;
import java.util.List;

public class FieldExpressionMap extends ExpressionMap {

	public List<String> generateStrings(Iteration i) {
		List<String> set = new ArrayList<>();
		
		if(expression instanceof RDFNodeConstant) {
			// It is assumed to be a string, otherwise the shapes
			// Would have caught the error.
			set.add(((RDFNodeConstant) expression).constant.toString());
		}
		else if(expression instanceof Template) {
			set.addAll(((Template) expression).values(i));
		}
		else if(expression instanceof Reference) {
			for(Object o : ((Reference) expression).values(i))
				set.add(o.toString());
		}

		return set;
	}

}