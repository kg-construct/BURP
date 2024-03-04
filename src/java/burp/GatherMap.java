package burp;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

public class GatherMap {
	
	public boolean allowEmptyListAndContainer = true;
	public Resource gatherAs = null;
	public Resource strategy = RML.append;
	public List<TermMap> termMaps = new ArrayList<TermMap>();
	
	public List<CC> generateCC(Iteration i, String baseIRI) {
//		List<CC> ccs = new ArrayList<CC>();
//		CC cc = null;
//		
//		if(gatherAs.equals(RDF.List))
//			cc = new Collection();
//		else if(gatherAs.equals(RDF.Alt))
//			cc = new Alt();
//		else if(gatherAs.equals(RDF.Bag))
//			cc = new Bag();
//		else if(gatherAs.equals(RDF.Seq))
//			cc = new Seq();
//		
//		for(Gatherable g : maps) {
//			if(g.isGatherMap()) {
//				cc = om.gatherMap.generateCC(i);
//			}
//			
//			for(RDFNode n : om.generateTerms(i, baseIRI)) {
//				if(cc != null) ccMap.put(n, cc);
//				objects.add(n);
//			}
//		}
		
		if(RML.append.equals(strategy)) {
			return append(i, baseIRI);
		} else if(RML.cartesianProduct.equals(strategy)) {
			return cartesianProduct(i, baseIRI);
		}
		
		throw new RuntimeException("Unknown CC strategy.");
	}

	private List<CC> cartesianProduct(Iteration i, String baseIRI) {
		// TODO Auto-generated method stub
		return null;
	}

	private List<CC> append(Iteration i, String baseIRI) {
		List<CC> ccs = new ArrayList<CC>();
		
		CC cc = prepareCC();
		
		for(TermMap tm : termMaps) {
			if(tm.isGatherMap()) {
				throw new RuntimeException("Nested gather maps not yet supported.");
			} else {
				for(RDFNode n : tm.generateTerms(i, baseIRI)) {
					cc.nodes.add(new Node(n));
				}
			}
		}
		
		if(cc.nodes.size() > 0 || allowEmptyListAndContainer)
			ccs.add(cc);
		
		return ccs;
	}

	private CC prepareCC() {
		if(gatherAs.equals(RDF.List)) return new BurpCollection();
		else if(gatherAs.equals(RDF.Alt)) return new BurpAlt();
		else if(gatherAs.equals(RDF.Bag)) return new BurpBag();
		else if(gatherAs.equals(RDF.Seq)) return new BurpSeq();
		throw new RuntimeException("Unknown CC type.");
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
	
	public List<Node> nodes = new ArrayList<Node>();
	//public RDFNode node;
	
}

class BurpCollection extends CC {
	
}

class BurpBag extends CC {
	
}

class BurpSeq extends CC {
	
}

class BurpAlt extends CC {
	
}
