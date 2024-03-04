package burp;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

public class GatherMap {
	
	public boolean allowEmptyListAndContainer = false;
	public Resource gatherAs = null;
	public Resource strategy = RML.append;
	public List<TermMap> termMaps = new ArrayList<TermMap>();
	
	public List<CC> generateCC(Iteration i, String baseIRI) {
		
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
		cc.allowEmptyListAndContainer = allowEmptyListAndContainer;
		
		for(TermMap tm : termMaps) {
			if(tm.isGatherMap()) {
				cc.collectables.addAll(tm.gatherMap.generateCC(i, baseIRI));
			} else {
				for(RDFNode n : tm.generateTerms(i, baseIRI)) {
					cc.collectables.add(new Node(n));
				}
			}
		}
		
		if(cc.collectables.size() > 0 || allowEmptyListAndContainer) {
			ccs.add(cc);
		}
		
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
