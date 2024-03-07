package burp;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;

public class ReferencingObjectMap implements GatherMap {
	
	public TriplesMap parent = null;
	public List<JoinCondition> joinConditions = new ArrayList<JoinCondition>();
	
	public GatherMapMixin gatherMap = null;
	
	@Override
	public boolean isGatherMap() {
		return gatherMap != null;
	}
	
	@Override
	public List<SubGraph> generateGatherMapGraphs(Iteration i, String baseIRI) {
		if(!isGatherMap())
			throw new RuntimeException("Trying to process a non-gathermap as gathermap");
		
		List<SubGraph> g = new ArrayList<SubGraph>();
		
		for(RDFNode n : generateTerms(i, baseIRI)) {
			SubGraph sg = new SubGraph(n, ModelFactory.createDefaultModel());
			g.add(sg);
		}
		
		return g;
	}

	@Override
	public List<RDFNode> generateTerms(Iteration i, String baseIRI) {
		// If there are no join conditions, then we generate resources
		// from the child iteration. This is only guaranteed to work
		// for logical sources of the same type or if the parent triple
		// map' subject map only uses simple references.
		if(joinConditions.isEmpty()) {
			return parent.subjectMap.generateTerms(i, baseIRI);			
		} else {
			List<RDFNode> list = new ArrayList<RDFNode>();
			Iterator<Iteration> iter2 = parent.logicalSource.iterator();
			while (iter2.hasNext()) {
				Iteration i2 = iter2.next();

				// Expression Maps are multi-valued. We thus need
				// For each join condition at least one match.
				boolean ok = true;
				for (JoinCondition jc : joinConditions) {

					List<String> values1 = jc.childMap.generateValues(i);
					List<String> values2 = jc.parentMap.generateValues(i2);

					if (values1.stream().distinct().filter(values2::contains)
							.collect(Collectors.toSet()).isEmpty()) {
						// No match, break.
						ok = false;
						break;
					}
				}

				if (ok) {
					list.addAll(parent.subjectMap.generateTerms(i2, baseIRI));
				}
			}
			
			return list;
		}
	}

}

class JoinCondition {
	
	public ConcreteExpressionMap parentMap = null;
	public ConcreteExpressionMap childMap = null;

}