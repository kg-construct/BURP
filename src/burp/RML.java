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
//	public static final Resource BaseTableOrView = ResourceFactory.createResource(NS + "BaseTableOrView");
//	public static final Resource GraphMap = ResourceFactory.createResource(NS + "GraphMap");
//	public static final Resource LogicalTable = ResourceFactory.createResource(NS + "LogicalTable");
//	public static final Resource ObjectMap = ResourceFactory.createResource(NS + "ObjectMap");
//	public static final Resource R2RMLView = ResourceFactory.createResource(NS + "R2RMLView");
//	public static final Resource RefObjectMap = ResourceFactory.createResource(NS + "RefObjectMap");
//	public static final Resource SubjectMap = ResourceFactory.createResource(NS + "SubjectMap");
//	public static final Resource TermMap = ResourceFactory.createResource(NS + "TermMap");
//	public static final Resource TriplesMap = ResourceFactory.createResource(NS + "TriplesMap");
	
	// Properties
	public static final Property clazz = ResourceFactory.createProperty(NS + "class");
	public static final Property constant = ResourceFactory.createProperty(NS + "constant");
	public static final Property datatypeMap  = ResourceFactory.createProperty(NS + "datatypeMap");
	public static final Property graphMap  = ResourceFactory.createProperty(NS + "graphMap");
	public static final Property iterator  = ResourceFactory.createProperty(NS + "iterator");
	public static final Property languageMap  = ResourceFactory.createProperty(NS + "languageMap");
	public static final Property logicalSource = ResourceFactory.createProperty(NS + "logicalSource");
	public static final Property objectMap  = ResourceFactory.createProperty(NS + "objectMap");
	public static final Property predicateMap  = ResourceFactory.createProperty(NS + "predicateMap");
	public static final Property predicateObjectMap  = ResourceFactory.createProperty(NS + "predicateObjectMap");
	public static final Property reference = ResourceFactory.createProperty(NS + "reference");
	public static final Property referenceFormulation = ResourceFactory.createProperty(NS + "referenceFormulation");
	public static final Property source = ResourceFactory.createProperty(NS + "source");
	public static final Property subjectMap  = ResourceFactory.createProperty(NS + "subjectMap");
	public static final Property template = ResourceFactory.createProperty(NS + "template");
	public static final Property termType = ResourceFactory.createProperty(NS + "termType");
	
}