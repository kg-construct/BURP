package burp;

import java.util.List;

import org.apache.jena.rdf.model.RDFNode;

public interface GatherMap {

	public abstract boolean isGatherMap();
	
	abstract public List<SubGraph> generateGatherMapGraphs(Iteration i, String baseIRI);

	abstract public List<RDFNode> generateTerms(Iteration i, String baseIRI);
	
}
