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
	public List<TermMap> maps = new ArrayList<TermMap>();
	
	public CC generateCC(Iteration i) {
		CC cc = null;
		
		if(gatherAs.equals(RDF.List))
			cc = new Collection();
		else if(gatherAs.equals(RDF.Alt))
			cc = new Alt();
		else if(gatherAs.equals(RDF.Bag))
			cc = new Bag();
		else if(gatherAs.equals(RDF.Seq))
			cc = new Seq();
		
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
			cc = append(i);
		} else if(RML.cartesianProduct.equals(strategy)) {
			cc = cartesianProduct(i);
		}
		
		return cc;
	}

	private CC cartesianProduct(Iteration i) {
		// TODO Auto-generated method stub
		return null;
	}

	private CC append(Iteration i) {
		// TODO Auto-generated method stub
		return null;
	}
	
}

interface Collectable {
	
}

class Node implements Collectable {
	public RDFNode node = null;
}

abstract class CC implements Collectable {
	
	public List<Node> nodes = new ArrayList<Node>();

}

class Collection extends CC {
	
}

class Bag extends CC {
	
}

class Seq extends CC {
	
}

class Alt extends CC {
	
}
