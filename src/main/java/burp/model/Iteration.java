package burp.model;

import java.util.List;
import java.util.Set;

public abstract class Iteration {
	
	public Set<Object> nulls = null;
	
	public Iteration(Set<Object> nulls) {
		this.nulls = nulls;
	}

	public abstract List<Object> getValuesFor(String reference);

	public abstract List<String> getStringsFor(String reference);

}