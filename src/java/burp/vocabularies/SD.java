package burp.vocabularies;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

public class SD {
	
	public static final String NS = "http://www.w3.org/ns/sparql-service-description#";
	
	public static final Resource Service = ResourceFactory.createResource(NS + "Service");

	public static final Property endpoint  = ResourceFactory.createProperty(NS + "endpoint");

}
