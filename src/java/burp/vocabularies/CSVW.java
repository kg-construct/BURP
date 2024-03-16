package burp.vocabularies;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

public class CSVW {
	
	public static final String NS = "http://www.w3.org/ns/dcat#";
	
	public static final Resource Table = ResourceFactory.createResource(NS + "Table");

	public static Property delimiter = ResourceFactory.createProperty(NS + "delimiter");
	public static Property dialect = ResourceFactory.createProperty(NS + "dialect");
	public static Property encoding = ResourceFactory.createProperty(NS + "encoding");
	public static Property header = ResourceFactory.createProperty(NS + "header");
	public static Property url = ResourceFactory.createProperty(NS + "url");

}