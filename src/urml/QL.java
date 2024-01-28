package urml;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;


public final class QL {
	
	public static final String NS = "http://w3id.org/rml/ql/";
	
	public static final Resource CSV = ResourceFactory.createResource(NS + "CSV");
	public static final Resource JSONPath = ResourceFactory.createResource(NS + "JSONPath");
	public static final Resource XPath = ResourceFactory.createResource(NS + "XPath");
	
		
}