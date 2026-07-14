package burp.model;

import java.util.List;

import org.apache.jena.rdf.model.RDFNode;

import burp.model.gathermaputil.SubGraph;

public interface GatherMap {

	boolean isGatherMap();
	
	List<SubGraph> generateGatherMapGraphs(Iteration i, String baseIRI);

	List<RDFNode> generateTerms(Iteration i, String baseIRI);
	
}
