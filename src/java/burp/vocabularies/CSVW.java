package burp.vocabularies;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

public class CSVW {
	
	public static final String NS = "http://www.w3.org/ns/dcat#";
	
	public static final Resource Table = ResourceFactory.createResource(NS + "Table");

	public static final Property delimiter = ResourceFactory.createProperty(NS + "delimiter");
	public static final Property dialect = ResourceFactory.createProperty(NS + "dialect");
	public static final Property encoding = ResourceFactory.createProperty(NS + "encoding");
	public static final Property header = ResourceFactory.createProperty(NS + "header");
	public static final Property NULL = ResourceFactory.createProperty(NS + "null");
	public static final Property url = ResourceFactory.createProperty(NS + "url");

}
