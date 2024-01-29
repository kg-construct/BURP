package urml;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.jena.rdf.model.RDFNode;

public abstract class Expression {

	abstract protected Set<Object> values(Iteration i);

}

class Constant extends Expression {
	
	public String constant = null;
	
	public Constant(String constant) {
		this.constant = constant;
	}

	@SuppressWarnings("rawtypes")
	@Override
	// If the term map is a constant-valued term map, 
	// then the generated RDF term is the term map's 
	// constant value.
	protected Set<Object> values(Iteration i) {
		Set<Object> set = new HashSet<Object>();
		set.add(constant);
		return set;
	}
	
}

class TermConstant extends Expression {
	
	public Object constant = null;
	
	public TermConstant(RDFNode constant) {
		this.constant = constant;
	}

	@SuppressWarnings("rawtypes")
	@Override
	// If the term map is a constant-valued term map, 
	// then the generated RDF term is the term map's 
	// constant value.
	protected Set<Object> values(Iteration i) {
		Set<Object> set = new HashSet<Object>();
		set.add(constant);
		return set;
	}
	
}

class Template extends Expression {
	
	public String template = null;
	
	public Template(String template) {
		// TODO: Validate the template
		this.template = template;
	}

	@Override
	// If the term map is a template-valued term map, 
	// then the generated RDF term is determined by applying 
	// the term generation rules to its template value.
	protected Set<Object> values(Iteration i) {
		Set<String> set = new HashSet<String>();
		set.add(template);
		
		for(String reference : references()) {
			Set<String> valuesForReference = i.getStringsFor(reference);			
			Set<String> newset = new HashSet<String>();
			
			String search = "{" + StringEscapeUtils.escapeJava(reference) + "}";
			
			for(String s : set)
				for(String v : valuesForReference)
					if(v != null)
						newset.add(s.replace(search, v));

			set = newset;
		}
		
		return new HashSet<Object>(set);
		
	}

	private List<String> references() {
		List<String> list = new ArrayList<String>();
		Matcher m = Pattern.compile("(?<!\\\\)\\{(.+?)(?<!\\\\)\\}").matcher(template);
		while(m.find()) {
			String temp = template.substring(m.start(1), m.end(1));
			list.add(StringEscapeUtils.unescapeJava(temp));
		}
		return list;
	}
	
}

class Reference extends Expression {
	
	public String reference = null;
	
	public Reference(String reference) {
		this.reference = reference;
	}

	@Override
	// If the term map is a reference-valued term map, 
	// then the generated RDF term is determined by applying the 
	// term generation rules to its reference value.
	protected Set<Object> values(Iteration i) {
		//
		return i.getValuesFor(reference);
	}
	
}

