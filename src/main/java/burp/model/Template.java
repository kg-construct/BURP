package burp.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;

import burp.util.Util;

public class Template extends Expression {
	
	public String template = null;
	
	public Template(String template) {
		this.template = template;
	}

	// If the term map is a template-valued term map, 
	// then the generated RDF term is determined by applying 
	// the term generation rules to its template value.
	public List<String> values(Iteration i) {
		return values(i, false);
	}
	
	public List<String> values(Iteration i, boolean safe) {
		List<String> list = new ArrayList<String>();
		list.add(template);
		
		for(String reference : references()) {
			List<String> valuesForReference = i.getStringsFor(reference);
			List<String> newset = new ArrayList<String>();
			
			String search = "{" + StringEscapeUtils.escapeJava(reference) + "}";
			
			for(String s : list)
				for(String v : valuesForReference)
					if(v != null) {
						newset.add(s.replace(search, safe ? Util.toIRISafe(v) : v));
					}

			list = newset;
		}
		
		list = list.stream().
				map((s)-> s.replace("\\{", "{").replace("\\}", "}")).
				collect(Collectors.toList());
		
		return list;
		
	}

	private static Pattern p = Pattern.compile("(?<!\\\\)\\{(.+?)(?<!\\\\)\\}");
	private List<String> references() {
		List<String> list = new ArrayList<String>();
		Matcher m = p.matcher(template);
		while(m.find()) {
			String temp = template.substring(m.start(1), m.end(1));
			list.add(StringEscapeUtils.unescapeJava(temp));
		}
		return list;
	}

}