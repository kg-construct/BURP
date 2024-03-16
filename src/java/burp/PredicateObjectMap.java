package burp;

import java.util.ArrayList;
import java.util.List;

import burp.em.GraphMap;
import burp.em.ObjectMap;
import burp.em.PredicateMap;

public class PredicateObjectMap {

	public List<PredicateMap> predicateMaps = new ArrayList<PredicateMap>();
	public List<ObjectMap> objectMaps = new ArrayList<ObjectMap>();
	public List<ReferencingObjectMap> refObjectMaps = new ArrayList<ReferencingObjectMap>();
	public List<GraphMap> graphMaps = new ArrayList<GraphMap>();
}
