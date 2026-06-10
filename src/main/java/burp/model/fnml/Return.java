package burp.model.fnml;

import java.util.HashMap;
import java.util.Map;

public class Return {

	private final Map<String, Object> returns = new HashMap<>();

	public Object defaultValue;
	public Map<String, Object> additionalOutputs;

	public Return(Object defaultValue) {
		this.defaultValue = defaultValue;
		this.additionalOutputs = Map.of();
	}

	public Return(Object defaultValue, Map<String, Object> additionalOutputs) {
		this.defaultValue = defaultValue;
		this.additionalOutputs = additionalOutputs;
		if (additionalOutputs != null) {
			this.returns.putAll(additionalOutputs);
		}
	}

	public Object get(Object key) {
		if (!returns.containsKey(key.toString()) && !returns.isEmpty()) {
			throw new RuntimeException("Unknown return value %s.".formatted(key));
		}
		return returns.get(key.toString());
	}

	public Object put(String key, Object value) {
		return returns.put(key, value);
	}
	
}
