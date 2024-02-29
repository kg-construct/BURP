package burp;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.ResourceFactory;

public class Test {

	public static void main(String[] args) {
		Model m = ModelFactory.createDefaultModel();
		
		Model m2 = ModelFactory.createDefaultModel();
				
		RDFList r = m2.createList(ResourceFactory.createPlainLiteral("1"));
		
		//Resource r2 = m2.createResource();
		
		r.setTail(m2.createList(ResourceFactory.createPlainLiteral("1"), ResourceFactory.createPlainLiteral("2")));
		
		r.with(ResourceFactory.createPlainLiteral("2"));
		
		m.add(m2);
		
		m.write(System.out, "Turtle");

	}

}
