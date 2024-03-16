package burp.model.gathermaputil;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.rdf.model.impl.StatementImpl;
import org.apache.jena.vocabulary.RDF;

public class SubGraph {

	public RDFNode node;
	public Model model;

	public SubGraph(RDFNode n, Model m) {
		this.node = n;
		this.model = m;
	}

	public SubGraph() {}

	public void updateNode(RDFNode n) {	
		StmtIterator iter = model.listStatements();
		List<Statement> news = new ArrayList<Statement>();
		List<Statement> olds = new ArrayList<Statement>();
		while(iter.hasNext()) {
			Statement s = iter.next();
			if(s.getSubject().equals(node)) {
				olds.add(s);
				news.add(new StatementImpl(n.asResource(), s.getPredicate(), s.getObject()));
			}
		}
		model.add(news);
		model.remove(olds);
		
		node = n;
	}

	public boolean isList() { return !isBag() && !isSeq() && !isAlt(); }
	public boolean isAlt() { return model.contains(node.asResource(), RDF.type, RDF.Alt); }
	public boolean isBag() { return model.contains(node.asResource(), RDF.type, RDF.Bag); }
	public boolean isSeq() { return model.contains(node.asResource(), RDF.type, RDF.Seq); }
	
	public String toString() {
		return node == null ? null : node.toString();
	}
}
