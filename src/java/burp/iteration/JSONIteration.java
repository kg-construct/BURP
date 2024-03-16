package burp.iteration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

import burp.model.Iteration;

public class JSONIteration extends Iteration {

	private DocumentContext doc = null;
	
	private static Configuration c = Configuration
			.builder()
            .mappingProvider(new JacksonMappingProvider())
            .jsonProvider(new JacksonJsonProvider())
            .build()
            .addOptions(Option.ALWAYS_RETURN_LIST)
			;
	
	public JSONIteration(String json, Set<Object> nulls) {
		super(nulls);
		
		doc = JsonPath.using(c).parse(json);
	}

	@Override
	public List<Object> getValuesFor(String reference) {
		// We need to explicitly convert the objects
		// to strings because RML has not worked out
		// "6.6.1 Automatically deriving datatypes" yet
		List<Object> l2 = new ArrayList<Object>();
		try {
			List<Object> l = doc.read(reference);
			for(Object o : l)
				if(o != null && !nulls.contains(o))
					l2.add(o.toString());
		} catch (PathNotFoundException e) {
			// No data, silently ignore
			e.printStackTrace();
		}
		return l2;
	}

	@Override
	public List<String> getStringsFor(String reference) {
		// We need to explicitly convert the objects
		// to strings (when they are null) because 
		// this JSONPath library is... difficult.
		List<String> l2 = new ArrayList<String>();
		try {
			List<Object> l = doc.read(reference);
			for(Object o : l)
				if(o != null && !nulls.contains(o))
					l2.add(o.toString());
		} catch (PathNotFoundException e) {
			// No data, silently ignore
			e.printStackTrace();
		}
		return l2;
	}
	
}