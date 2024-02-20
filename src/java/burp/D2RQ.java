package burp;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;


public final class D2RQ {
	
	public static final String NS = "http://www.wiwiss.fu-berlin.de/suhl/bizer/D2RQ/0.1#";
	
	public static final Property jdbcDSN = ResourceFactory.createProperty(NS + "jdbcDSN");
	public static final Property jdbcDriver = ResourceFactory.createProperty(NS + "jdbcDriver");
	public static final Property username = ResourceFactory.createProperty(NS + "username");
	public static final Property password = ResourceFactory.createProperty(NS + "password");

	
		
}