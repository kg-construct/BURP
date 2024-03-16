package burp.model;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.query.Dataset;

public class TriplesMap {

	public LogicalSource logicalSource = null;
	public SubjectMap subjectMap = null;
	public List<PredicateObjectMap> predicateObjectMaps = new ArrayList<PredicateObjectMap>();

	public void generateInto(Dataset ds) {
		// TODO Auto-generated method stub
		
	}

}