package burp.exp;

import java.util.List;

import burp.iteration.Iteration;

public class Reference extends Expression {
	
	public String reference = null;
	
	public Reference(String reference) {
		this.reference = reference;
	}

	// If the term map is a reference-valued term map, 
	// then the generated RDF term is determined by applying the 
	// term generation rules to its reference value.
	public List<Object> values(Iteration i) {
		return i.getValuesFor(reference);
	}
	
}