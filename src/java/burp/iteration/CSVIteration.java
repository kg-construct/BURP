package burp.iteration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import burp.model.Iteration;

public class CSVIteration extends Iteration {

	private Map<String, String> map = new HashMap<String, String>();
	
	public CSVIteration(String[] header, String[] rec, Set<Object> nulls) {
		super(nulls);
		
		for(int i = 0; i < header.length; i++) {
			map.put(header[i], rec[i]);
		}
	}

	@Override
	public List<Object> getValuesFor(String reference) {
		List<Object> l = new ArrayList<Object>();
		if(!map.containsKey(reference))
			throw new RuntimeException("Attribute " + reference + " does not exist.");
		
		String o = map.get(reference);
		if(!nulls.contains(o))
			l.add(o);
		
		return l;
	}

	@Override
	public List<String> getStringsFor(String reference) {
		List<String> l = new ArrayList<String>();
		if(!map.containsKey(reference))
			throw new RuntimeException("Attribute " + reference + " does not exist.");
		
		String o = map.get(reference);
		if(!nulls.contains(o))
			l.add(o);
		
		return l;
	}
	
}