package burp;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;


public final class RML {
	
	public static final String NS = "http://w3id.org/rml/";
	
	public static final Resource BLANKNODE = ResourceFactory.createResource(NS + "BlankNode");
	public static final Resource IRI = ResourceFactory.createResource(NS + "IRI");
	public static final Resource LITERAL = ResourceFactory.createResource(NS + "Literal");
	
	// RML Constants
	public static final Resource defaultGraph = ResourceFactory.createResource(NS + "defaultGraph");
	public static final Resource SQL2008 = ResourceFactory.createResource(NS + "SQL2008");

	// Classes
	
	// Properties
	public static final Property clazz = ResourceFactory.createProperty(NS + "class");
	public static final Property childMap = ResourceFactory.createProperty(NS + "childMap");
	public static final Property constant = ResourceFactory.createProperty(NS + "constant");
	public static final Property datatypeMap  = ResourceFactory.createProperty(NS + "datatypeMap");
	public static final Property graphMap  = ResourceFactory.createProperty(NS + "graphMap");
	public static final Property iterator  = ResourceFactory.createProperty(NS + "iterator");
	public static final Property joinCondition  = ResourceFactory.createProperty(NS + "joinCondition");
	public static final Property languageMap  = ResourceFactory.createProperty(NS + "languageMap");
	public static final Property logicalSource = ResourceFactory.createProperty(NS + "logicalSource");
	public static final Property objectMap  = ResourceFactory.createProperty(NS + "objectMap");
	public static final Property path  = ResourceFactory.createProperty(NS + "path");
	public static final Property parentMap  = ResourceFactory.createProperty(NS + "parentMap");
	public static final Property parentTriplesMap  = ResourceFactory.createProperty(NS + "parentTriplesMap");
	public static final Property predicateMap  = ResourceFactory.createProperty(NS + "predicateMap");
	public static final Property predicateObjectMap  = ResourceFactory.createProperty(NS + "predicateObjectMap");
	public static final Property reference = ResourceFactory.createProperty(NS + "reference");
	public static final Property referenceFormulation = ResourceFactory.createProperty(NS + "referenceFormulation");
	public static final Property source = ResourceFactory.createProperty(NS + "source");
	public static final Property sqlQuery = ResourceFactory.createProperty(NS + "sqlQuery");
	public static final Property sqlVersion = ResourceFactory.createProperty(NS + "sqlVersion");
	public static final Property subjectMap  = ResourceFactory.createProperty(NS + "subjectMap");
	public static final Property tableName = ResourceFactory.createProperty(NS + "tableName");
	public static final Property template = ResourceFactory.createProperty(NS + "template");
	public static final Property termType = ResourceFactory.createProperty(NS + "termType");
	
}