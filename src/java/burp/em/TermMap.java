package burp.em;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;

import burp.GatherMap;
import burp.GatherMapMixin;
import burp.SubGraph;
import burp.iteration.Iteration;

public abstract class TermMap extends ExpressionMap implements GatherMap {

	public Resource termType;
	//public abstract List<RDFNode> generateTerms(Iteration i, String baseIRI);
	public GatherMapMixin gatherMap = null;
	
	@Override
	public List<SubGraph> generateGatherMapGraphs(Iteration i, String baseIRI) {
		if(!isGatherMap())
			throw new RuntimeException("Trying to process a non-gathermap as gathermap");
		
		List<SubGraph> g = new ArrayList<SubGraph>();
		
		if(expression == null) {
			for(SubGraph sg : gatherMap.generateGraphs(i, baseIRI)) {
				g.add(sg);
			}
		} else {
			for(RDFNode n : generateTerms(i, baseIRI)) {
				for(SubGraph sg : gatherMap.generateGraphs(i, baseIRI)) {
					sg.updateNode(n);
					g.add(sg);
				}
			}
		}
		
		return g;
	}

}