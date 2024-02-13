package burp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
