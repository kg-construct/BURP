package burp.vocabularies;

import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.OntSpecification;
import org.apache.jena.ontapi.model.*;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

/**
 * Vocabulary definitions from src/main/resources/vocabularies/ptr.ttl.
 */
@SuppressWarnings("unused")
public class PTR {

    private static final OntModel M_MODEL = OntModelFactory.createModel(OntSpecification.OWL2_FULL_MEM);

    public static final String NS = "https://w3id.org/dre/ptr#";

    public static String getURI() {
        return NS;
    }

    public static final Resource NAMESPACE = M_MODEL.createResource(NS);

    public static final String VERSION_INFO = "0.0.1";

    /**
     * Ending point of the text range.
     * <p>Domain: ptr:Range</p>
     * <p>Range: ptr:Point</p>
     */
    public static final OntObjectProperty.Named end = M_MODEL.createObjectProperty("https://w3id.org/dre/ptr#end");

    /**
     * Whether the subject, predicate, or object of the statement is implicated.
     * <p>Domain: ptr:StatementPart</p>
     * <p>Range: ptr:StatementPartDescriptor</p>
     */
    public static final OntObjectProperty.Named part = M_MODEL.createObjectProperty("https://w3id.org/dre/ptr#part");

    /**
     * The character range in the text file.
     * <p>Domain: ptr:TextFilePointer</p>
     * <p>Range: ptr:Range</p>
     */
    public static final OntObjectProperty.Named range = M_MODEL.createObjectProperty("https://w3id.org/dre/ptr#range");

    /**
     * Starting point of the text range.
     * <p>Domain: ptr:Range</p>
     * <p>Range: ptr:Point</p>
     */
    public static final OntObjectProperty.Named start = M_MODEL.createObjectProperty("https://w3id.org/dre/ptr#start");

    /**
     * The RDF statement to which this part belongs.
     * <p>Domain: ptr:StatementPart</p>
     * <p>Range: rdf:Statement</p>
     */
    public static final OntObjectProperty.Named statement = M_MODEL.createObjectProperty("https://w3id.org/dre/ptr#statement");

    /**
     * Character range in the mapping text where the error occurs.
     * <p>Domain: ptr:StatementPart</p>
     * <p>Range: ptr:Range</p>
     */
    public static final OntObjectProperty.Named textRange = M_MODEL.createObjectProperty("https://w3id.org/dre/ptr#textRange");

    /**
     * Column number for the point.
     * <p>Domain: ptr:Point</p>
     * <p>Range: xsd:integer</p>
     */
    public static final OntDataProperty column = M_MODEL.createDataProperty("https://w3id.org/dre/ptr#column");

    /**
     * Line number for the point.
     * <p>Domain: ptr:Point</p>
     * <p>Range: xsd:integer</p>
     */
    public static final OntDataProperty line = M_MODEL.createDataProperty("https://w3id.org/dre/ptr#line");

    /**
     * The path of the file containing the reference.
     * <p>Domain: ptr:TextFilePointer</p>
     * <p>Range: xsd:string</p>
     */
    public static final OntDataProperty path = M_MODEL.createDataProperty("https://w3id.org/dre/ptr#path");

    /** Line/column coordinates inside a text. */
    public static final OntClass.Named Point = M_MODEL.createOntClass("https://w3id.org/dre/ptr#Point");

    /** Text span defined by start and end points. */
    public static final OntClass.Named Range = M_MODEL.createOntClass("https://w3id.org/dre/ptr#Range");

    /** Wrapper describing which part of an RDF statement is implicated. */
    public static final OntClass.Named StatementPart = M_MODEL.createOntClass("https://w3id.org/dre/ptr#StatementPart");

    /** Named descriptor of a statement position (subject, predicate, object). */
    public static final OntClass.Named StatementPartDescriptor = M_MODEL.createOntClass("https://w3id.org/dre/ptr#StatementPartDescriptor");

    /** Pointer pointing to a specific location in a text file. */
    public static final OntClass.Named TextFilePointer = M_MODEL.createOntClass("https://w3id.org/dre/ptr#TextFilePointer");

    public static final OntIndividual.Named Object = StatementPartDescriptor.createIndividual("https://w3id.org/dre/ptr#Object");

    public static final OntIndividual.Named Predicate = StatementPartDescriptor.createIndividual("https://w3id.org/dre/ptr#Predicate");

    public static final OntIndividual.Named Subject = StatementPartDescriptor.createIndividual("https://w3id.org/dre/ptr#Subject");

}
