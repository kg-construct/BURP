package burp.model;

import java.util.ArrayList;
import java.util.List;

public class TriplesMap {

	public AbstractLogicalSource logicalSource = null;
	public SubjectMap subjectMap = null;
	public List<PredicateObjectMap> predicateObjectMaps = new ArrayList<>();
	public String baseIRI = null;

}