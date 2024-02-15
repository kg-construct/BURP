package burp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

public abstract class Iteration {

	protected abstract List<Object> getValuesFor(String reference);

	protected abstract List<String> getStringsFor(String reference);

}

class CSVIteration extends Iteration {

	private Map<String, String> map = new HashMap<String, String>();
	
	public CSVIteration(String[] header, String[] rec) {
		for(int i = 0; i < header.length; i++) {
			map.put(header[i], rec[i]);
		}
	}

	@Override
	protected List<Object> getValuesFor(String reference) {
		List<Object> l = new ArrayList<Object>();
		if(map.containsKey(reference))
			l.add(map.get(reference));
		return l;
	}

	@Override
	protected List<String> getStringsFor(String reference) {
		List<String> l = new ArrayList<String>();
		if(map.containsKey(reference))
			l.add(map.get(reference));
		return l;
	}
	
}

class JSONIteration extends Iteration {

	private DocumentContext doc = null;
	
	private static Configuration c = Configuration
			.builder()
            .mappingProvider(new JacksonMappingProvider())
            .jsonProvider(new JacksonJsonProvider())
            .build()
            .addOptions(Option.ALWAYS_RETURN_LIST)
			;
	
	public JSONIteration(String json) {
		doc = JsonPath.using(c).parse(json);
	}

	@Override
	protected List<Object> getValuesFor(String reference) {
		// We need to explicitly convert the objects
		// to strings because RML has not worked out
		// "6.6.1 Automatically deriving datatypes" yet
		List<Object> l = doc.read(reference);
		List<Object> l2 = new ArrayList<Object>();
		for(Object o : l)
			if(o != null)
				l2.add(o.toString());
		return l2;
	}

	@Override
	protected List<String> getStringsFor(String reference) {
		// We need to explicitly convert the objects
		// to strings (when they are null) because 
		// this JSONPath library is... difficult.
		List<Object> l = doc.read(reference);
		List<String> l2 = new ArrayList<String>();
		for(Object o : l)
			if(o != null)
				l2.add(o.toString());
		return l2;
	}
	
}
