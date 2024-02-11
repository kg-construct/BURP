package burp;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class Iteration {

	protected abstract Set<Object> getValuesFor(String reference);

	protected abstract Set<String> getStringsFor(String reference);

}

class CSVIteration extends Iteration {

	private Map<String, String> map = new HashMap<String, String>();
	
	public CSVIteration(String[] header, String[] rec) {
		for(int i = 0; i < header.length; i++) {
			map.put(header[i], rec[i]);
		}
	}

	@Override
	protected Set<Object> getValuesFor(String reference) {
		Set<Object> set = new HashSet<Object>();
		if(map.containsKey(reference))
			set.add(map.get(reference));
		return set;
	}

	@Override
	protected Set<String> getStringsFor(String reference) {
		Set<String> set = new HashSet<String>();
		if(map.containsKey(reference))
			set.add(map.get(reference));
		return set;
	}
	
}
