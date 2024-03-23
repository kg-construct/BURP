package burp.model;

import java.util.HashMap;
import java.util.Map;

public class Return {

	private Map<String, Object> returns = new HashMap<String, Object>();

	public Object defaultValue = null;
	
	public Return(Object defaultValue) {
		this.defaultValue = defaultValue;
	}

	public Object get(Object key) {
		Object o = returns.get(key);
		
		if(o == null)
			throw new RuntimeException("Unknown return value %s.".formatted(key));
		
		return o;
	}

	public Object put(String key, Object value) {
		return returns.put(key, value);
	}
	
}
