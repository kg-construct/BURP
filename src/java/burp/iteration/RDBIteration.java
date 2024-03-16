package burp.iteration;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.text.StringEscapeUtils;

import burp.util.Util;

public class RDBIteration extends Iteration {

	private Map<String, Object> values = new HashMap<String, Object>();

	public RDBIteration(ResultSet resultSet, Map<String, Integer> indexMap, Set<Object> nulls) {
		super(nulls);
		
		for(String ref : indexMap.keySet()) {
			try {
				Object o = resultSet.getObject(indexMap.get(ref));
				if(o != null) {
					if(o instanceof byte[]) {
						o = Util.bytesToHexString((byte[]) o);
					}
				}
				
				values.put(ref, o);
			} catch (Exception e) {
				throw new RuntimeException("Error retrieving values from result set.");
			}
		}
	}
	
	@Override
	public List<Object> getValuesFor(String reference) {
		List<Object> l = new ArrayList<Object>();
		String columnname = StringEscapeUtils.unescapeJava(reference);		
		
		if(!values.containsKey(columnname) && !values.containsKey(columnname.replace("\"", "")))
			throw new RuntimeException("Attribute " + columnname + " does not exist.");
		
		Object value = values.get(columnname);
		
		// Check whether the user added the right column names in the mappings
		if(value == null)
			// Now try without quotes
			value = values.get(columnname.replace("\"", ""));
				
		if(value != null && !nulls.contains(value))
			l.add(value);
		
		return l;
	}

	@Override
	public List<String> getStringsFor(String reference) {
		List<String> l = new ArrayList<String>();
		for(Object o : getValuesFor(reference))
			if(o != null)
				l.add(o.toString());
		return l;
	}
	
}