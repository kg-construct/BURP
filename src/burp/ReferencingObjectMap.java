package burp;

import java.util.ArrayList;
import java.util.List;

public class ReferencingObjectMap {
	
	public TriplesMap parent = null;
	public List<JoinCondition> joinConditions = new ArrayList<JoinCondition>();

}

class JoinCondition {
	
	public ConcreteExpressionMap parentMap = null;
	public ConcreteExpressionMap childMap = null;

}