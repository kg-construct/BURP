package burp.vocabularies;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;


public final class Observable {

	public static final String NS = "https://ontology.unifiedcyberontology.org/uco/observable/";

	public static final Resource SocketAddress = ResourceFactory.createResource(NS + "SocketAddress");
	public static final Property addressValue = ResourceFactory.createProperty(NS + "addressValue");

	public static final Resource UserAccount = ResourceFactory.createResource(NS + "UserAccount");
	public static final Property userName = ResourceFactory.createProperty(NS + "userName");
	public static final Property hasFacet = ResourceFactory.createProperty(NS + "hasFacet");

	public static final Resource AccountAuthenticationFacet = ResourceFactory.createResource(NS + "AccountAuthenticationFacet");
	public static final Property password = ResourceFactory.createProperty(NS + "password");
	public static final Property passwordType = ResourceFactory.createProperty(NS + "passwordType");

}
