package burp;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.RDF;

public class Test {

	public static void main(String[] args) {
		Model m = ModelFactory.createDefaultModel();
		
		Resource r = m.createResource("http://foo.bar");
		
		//m.add(r, RDF.type, RDF.List);
		m.add(r, RDF.first, FOAF.page);
		m.add(r, RDF.rest, RDF.nil);
		
		RDFList x = m.getList(r);
		x.add(FOAF.Person);
		
		
		m.write(System.out, "Turtle");

	}

}
