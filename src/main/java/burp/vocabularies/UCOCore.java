package burp.vocabularies;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;


public final class UCOCore {

	//UCO Core
	public static final String NS = "https://ontology.unifiedcyberontology.org/uco/core/";

	public static final Property hasFacet = ResourceFactory.createProperty(NS + "hasFacet");

}
