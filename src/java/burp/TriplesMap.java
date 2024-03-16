package burp;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.query.Dataset;

import burp.em.SubjectMap;
import burp.ls.LogicalSource;

public class TriplesMap {

	public LogicalSource logicalSource = null;
	public SubjectMap subjectMap = null;
	public List<PredicateObjectMap> predicateObjectMaps = new ArrayList<PredicateObjectMap>();

	public void generateInto(Dataset ds) {
		// TODO Auto-generated method stub
		
	}

}