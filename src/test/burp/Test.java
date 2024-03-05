package burp;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Seq;
import org.apache.jena.vocabulary.RDF;

public class Test {

	public static void main(String[] args) {
		Model m = ModelFactory.createDefaultModel();
		
		Resource x = ResourceFactory.createResource("http://foo.bar/");
		m.add(x, RDF.type, RDF.Bag);
		
		Seq s = m.createSeq(x.getURI());
		
		s.add(1.90);
		
		m.write(System.err);
	}

}
