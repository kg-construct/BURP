package burp;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.rdf.model.Container;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

public class GatherMap {
	
	public boolean allowEmptyListAndContainer = false;
	public Resource gatherAs = null;
	public Resource strategy = RML.append;
	public List<TermMap> termMaps = new ArrayList<TermMap>();
	
//	public List<CC> generateCC(Iteration i, String baseIRI) {
//		
//		if(RML.append.equals(strategy)) {
//			return append(i, baseIRI);
//		} else if(RML.cartesianProduct.equals(strategy)) {
//			return cartesianProduct(i, baseIRI);
//		}
//		
//		throw new RuntimeException("Unknown CC strategy.");
//	}
//
//	private List<CC> cartesianProduct(Iteration i, String baseIRI) {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	private List<CC> append(Iteration i, String baseIRI) {
//		List<CC> ccs = new ArrayList<CC>();
//		
//		CC cc = prepareCC();
//		cc.allowEmptyListAndContainer = allowEmptyListAndContainer;
//		
//		for(TermMap tm : termMaps) {
//			if(tm.isGatherMap()) {
//				cc.collectables.addAll(tm.gatherMap.generateCC(i, baseIRI));
//			} else {
//				for(RDFNode n : tm.generateTerms(i, baseIRI)) {
//					cc.collectables.add(new Node(n));
//				}
//			}
//		}
//		
//		if(cc.collectables.size() > 0 || allowEmptyListAndContainer) {
//			ccs.add(cc);
//		}
//		
//		return ccs;
//	}

//	private CC prepareCC() {
//		if(gatherAs.equals(RDF.List)) return new BurpCollection();
//		else if(gatherAs.equals(RDF.Alt)) return new BurpAlt();
//		else if(gatherAs.equals(RDF.Bag)) return new BurpBag();
//		else if(gatherAs.equals(RDF.Seq)) return new BurpSeq();
//		throw new RuntimeException("Unknown CC type.");
//	}

	public Model generateGraph(RDFNode n, Iteration i, String baseIRI) {
		if (RML.append.equals(strategy)) {
			return append(n, i, baseIRI);
		} else if (RML.cartesianProduct.equals(strategy)) {
			//return cartesianProduct(i, baseIRI);
			throw new RuntimeException("Cartesian Product not yet implemented.");
		}
		throw new RuntimeException("Unknown strategy.");
	}

	private Model append(RDFNode n, Iteration i, String baseIRI) {
		Model m = ModelFactory.createDefaultModel();
		
		List<SubGraph> list = new ArrayList<SubGraph>();
		for(TermMap tm : termMaps) {
			if(tm.isGatherMap()) {
				throw new RuntimeException("Nested not yet supported");
			} else {
				for(RDFNode generated : tm.generateTerms(i, baseIRI)) {
					SubGraph sg = new SubGraph();
					sg.node = generated;
					list.add(sg);
				}
			}
		}
		
		System.err.println(list);
		
		if(gatherAs.equals(RDF.List))
			createList(m, n, list);
		else
			createContainer(m, n, list);
		
		return m;
	}

	private void createList(Model m, RDFNode n, List<SubGraph> list) {
		// TODO Auto-generated method stub
		
	}
	
	private void createContainer(Model m, RDFNode n, List<SubGraph> list) {
		if(list.size() > 0 || allowEmptyListAndContainer) {
			Container c = null;
			if(gatherAs.equals(RDF.Alt)) {
				m.add(n.asResource(), RDF.type, RDF.Alt);
				c = m.getAlt(n.asResource());
			} else if(gatherAs.equals(RDF.Bag)) {
				m.add(n.asResource(), RDF.type, RDF.Bag);
				c = m.getBag(n.asResource());
				
			} else if(gatherAs.equals(RDF.Seq)) {
				m.add(n.asResource(), RDF.type, RDF.Seq);
				c = m.getSeq(n.asResource());
			}
			
			for(SubGraph sg : list) {
				// Adding the element to the container
				c.add(sg.node);
				
				// Adding any triples around it into the model
				if(sg.model != null)
					m.add(sg.model);
			}
		}
	}

	
}	

interface Collectable {
	
}

class Node implements Collectable {
	
	public RDFNode node = null;
	
	public Node(RDFNode node) {
		this.node = node;
	}

}

abstract class CC implements Collectable {
	
	public boolean allowEmptyListAndContainer;
	public List<Collectable> collectables = new ArrayList<Collectable>();
	
}

class BurpCollection extends CC {
	
}

class BurpEmptpyCollection extends CC {
	
}

class BurpBag extends CC {
	
}

class BurpSeq extends CC {
	
}

class BurpAlt extends CC {
	
}
