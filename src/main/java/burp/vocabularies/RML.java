package burp.vocabularies;

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
	public static final Resource CSV = ResourceFactory.createResource(NS + "CSV");
	public static final Resource JSONPath = ResourceFactory.createResource(NS + "JSONPath");
	public static final Resource MappingDirectory = ResourceFactory.createResource(NS + "MappingDirectory");
	public static final Resource CurrentWorkingDirectory = ResourceFactory.createResource(NS + "CurrentWorkingDirectory");
	public static final Resource Namespace = ResourceFactory.createResource(NS + "Namespace");
	public static final Resource RelativePathSource = ResourceFactory.createResource(NS + "RelativePathSource");
	public static final Resource SQL2008Table = ResourceFactory.createResource(NS + "SQL2008Table");
	public static final Resource SQL2008Query = ResourceFactory.createResource(NS + "SQL2008Query");
	public static final Resource XPath = ResourceFactory.createResource(NS + "XPath");
	public static final Resource XPathReferenceFormulation = ResourceFactory.createResource(NS + "XPathReferenceFormulation");

	public static final Resource append = ResourceFactory.createResource(NS + "append");
	public static final Resource cartesianProduct = ResourceFactory.createResource(NS + "cartesianProduct");

	public static final Resource UTF8 = ResourceFactory.createResource(NS + "UTF-8");
	public static final Resource UTF16 = ResourceFactory.createResource(NS + "UTF-16");
	public static final Resource none = ResourceFactory.createResource(NS + "none");
	public static final Resource gzip = ResourceFactory.createResource(NS + "gzip");
	public static final Resource zip = ResourceFactory.createResource(NS + "zip");
	public static final Resource tarxz = ResourceFactory.createResource(NS + "tarxz");
	public static final Resource targz = ResourceFactory.createResource(NS + "targz");

	public static final String FORMATSNS = "http://www.w3.org/ns/formats/";
	public static final Resource SPARQL_Results_CSV = ResourceFactory.createResource(FORMATSNS + "SPARQL_Results_CSV");
	public static final Resource SPARQL_Results_TSV = ResourceFactory.createResource(FORMATSNS + "SPARQL_Results_TSV");
	public static final Resource SPARQL_Results_JSON = ResourceFactory.createResource(FORMATSNS + "SPARQL_Results_JSON");
	public static final Resource SPARQL_Results_XML = ResourceFactory.createResource(FORMATSNS + "SPARQL_Results_XML");

	// Classes

	// Properties
	public static final Property allowEmptyListAndContainer = ResourceFactory.createProperty(NS + "allowEmptyListAndContainer");
	public static final Property clazz = ResourceFactory.createProperty(NS + "class");
	public static final Property childMap = ResourceFactory.createProperty(NS + "childMap");
	public static final Property compression = ResourceFactory.createProperty(NS + "compression");
	public static final Property constant = ResourceFactory.createProperty(NS + "constant");
	public static final Property datatypeMap  = ResourceFactory.createProperty(NS + "datatypeMap");
	public static final Property encoding  = ResourceFactory.createProperty(NS + "encoding");
	public static final Property functionExecution  = ResourceFactory.createProperty(NS + "functionExecution");
	public static final Property functionMap  = ResourceFactory.createProperty(NS + "functionMap");
	public static final Property gather  = ResourceFactory.createProperty(NS + "gather");
	public static final Property gatherAs  = ResourceFactory.createProperty(NS + "gatherAs");
	public static final Property graphMap  = ResourceFactory.createProperty(NS + "graphMap");
	public static final Property input  = ResourceFactory.createProperty(NS + "input");
	public static final Property inputValueMap  = ResourceFactory.createProperty(NS + "inputValueMap");
	public static final Property iterator  = ResourceFactory.createProperty(NS + "iterator");
	public static final Property joinCondition  = ResourceFactory.createProperty(NS + "joinCondition");
	public static final Property languageMap  = ResourceFactory.createProperty(NS + "languageMap");
	public static final Property logicalSource = ResourceFactory.createProperty(NS + "logicalSource");
	public static final Property namespace  = ResourceFactory.createProperty(NS + "namespace");
	public static final Property namespacePrefix  = ResourceFactory.createProperty(NS + "namespacePrefix");
	public static final Property namespaceURL  = ResourceFactory.createProperty(NS + "namespaceURL");
	public static final Property NULL = ResourceFactory.createProperty(NS + "null");
	public static final Property objectMap  = ResourceFactory.createProperty(NS + "objectMap");
	public static final Property parameterMap  = ResourceFactory.createProperty(NS + "parameterMap");
	public static final Property path  = ResourceFactory.createProperty(NS + "path");
	public static final Property parentMap  = ResourceFactory.createProperty(NS + "parentMap");
	public static final Property parentTriplesMap  = ResourceFactory.createProperty(NS + "parentTriplesMap");
	public static final Property predicateMap  = ResourceFactory.createProperty(NS + "predicateMap");
	public static final Property predicateObjectMap  = ResourceFactory.createProperty(NS + "predicateObjectMap");
	public static final Property reference = ResourceFactory.createProperty(NS + "reference");
	public static final Property referenceFormulation = ResourceFactory.createProperty(NS + "referenceFormulation");
	public static final Property returnMap = ResourceFactory.createProperty(NS + "returnMap");
	public static final Property root = ResourceFactory.createProperty(NS + "root");
	public static final Property source = ResourceFactory.createProperty(NS + "source");
	public static final Property strategy = ResourceFactory.createProperty(NS + "strategy");
	public static final Property subjectMap  = ResourceFactory.createProperty(NS + "subjectMap");
	public static final Property template = ResourceFactory.createProperty(NS + "template");
	public static final Property termType = ResourceFactory.createProperty(NS + "termType");

	// Utility constants
	public static final Resource list = ResourceFactory.createResource(NS + "list");
	public static final Resource noempty = ResourceFactory.createResource(NS + "noEmpty");

}
