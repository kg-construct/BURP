package burp.iteration;

import java.util.List;
import java.util.Set;

public abstract class Iteration {
	
	public Set<Object> nulls = null;
	
	public Iteration(Set<Object> nulls) {
		this.nulls = nulls;
	}

	public abstract List<Object> getValuesFor(String reference);

	public abstract List<String> getStringsFor(String reference);

}

//class SPARQLTSVIteration extends Iteration {
//
//	private Map<String, Object> map = new HashMap<String, Object>();
//	private Model m = ModelFactory.createDefaultModel();
//	
//	public SPARQLTSVIteration(String[] header, String[] rec) {
//		for(int i = 0; i < header.length; i++) {
//			if(isResource(rec[i])) {
//				map.put(header[i], m.createResource(rec[i]));
//			} else if (rec[i].startsWith("_:")) {
//				Resource r = m.createResource(new AnonId(rec[i]));
//				map.put(header[i], r);
//			} else {
//				String triple = "<#a> <#b> " + rec[i] + ".";
//				Model m2 = ModelFactory.createDefaultModel().read(new StringReader(triple), "http://example.org/");
//				Statement s = m2.listStatements().next();
//				map.put(header[i], s.getObject());
//			}
//		}
//	}
//
//	private boolean isResource(String string) {
//		try {
//			IRIFactory.iriImplementation().create(string.toString());
//			return true;
//		} catch(Exception e) {
//			return false;
//		}
//	}
//
//	@Override
//	protected List<Object> getValuesFor(String reference) {
//		List<Object> l = new ArrayList<Object>();
//		if(!map.containsKey(reference))
//			throw new RuntimeException("Attribute " + reference + " does not exist.");
//		l.add(map.get(reference));
//		return l;
//	}
//
//	@Override
//	protected List<String> getStringsFor(String reference) {
//		List<String> l = new ArrayList<String>();
//		if(map.containsKey(reference))
//			l.add(map.get(reference).toString());
//		return l;
//	}
//	
//}