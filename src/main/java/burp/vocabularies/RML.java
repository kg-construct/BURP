package burp.vocabularies;

import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.OntSpecification;
import org.apache.jena.ontapi.model.*;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

/**
 * Jena OntAPI vocabulary definitions for RML.
 */
@SuppressWarnings("unused")
public class RML {

    private static final OntModel M_MODEL = OntModelFactory.createModel(OntSpecification.OWL2_FULL_MEM);

    public static final String NS = "http://w3id.org/rml/";

    public static String getURI() {
        return NS;
    }

    public static final Resource NAMESPACE = M_MODEL.createResource(NS);

    public static final String VERSION_INFO = "0.1.0";

    /**
     * Indicates the base IRI of the RDF graph to be created with a triples map.
     * <p>Domain: rml:TriplesMap</p>
     */
    public static final OntObjectProperty.Named baseIRI = M_MODEL.createObjectProperty("http://w3id.org/rml/baseIRI");

    /**
     * Names a child map in the child source of a join.
     * <p>Domain: rml:Join</p>
     * <p>Range: rml:ChildMap</p>
     */
    public static final OntObjectProperty.Named childMap = M_MODEL.createObjectProperty("http://w3id.org/rml/childMap");

    /**
     * The subject value generated for a logical source record will be asserted as an instance of this RDFS class.
     * <p>Domain: rml:SubjectMap</p>
     * <p>Range: rdfs:Class</p>
     */
    public static final OntObjectProperty.Named class_ = M_MODEL.createObjectProperty("http://w3id.org/rml/class");

    /**
     * Defines the compression format of a Source or Target.
     * <p>Domain: _:54f367c7081ae8ddefdc5318780f1240</p>
     * <p>Range: rml:Compression</p>
     */
    public static final OntObjectProperty.Named compression = M_MODEL.createObjectProperty("http://w3id.org/rml/compression");

    /**
     * Specifies the datatype of the object component for the generated triple.
     * <p>Domain: rml:ObjectMap</p>
     */
    public static final OntObjectProperty.Named datatype = M_MODEL.createObjectProperty("http://w3id.org/rml/datatype");

    /**
     * A DatatypetMap element to generate the datatype component of the object component from a logical source record.
     * <p>Domain: rml:ObjectMap</p>
     * <p>Range: rml:DatatypeMap</p>
     */
    public static final OntObjectProperty.Named datatypeMap = M_MODEL.createObjectProperty("http://w3id.org/rml/datatypeMap");

    /**
     * Defines the encoding format of a Source or Target.
     * <p>Domain: _:69b480a2fa3c118efc47914a328ed569</p>
     * <p>Range: rml:Encoding</p>
     */
    public static final OntObjectProperty.Named encoding = M_MODEL.createObjectProperty("http://w3id.org/rml/encoding");

    /**
     * A field of the logical view.
     * <p>Domain: _:856f571de45e3e79b9f8bd2f1f4568d0</p>
     * <p>Range: rml:Field</p>
     */
    public static final OntObjectProperty.Named field = M_MODEL.createObjectProperty("http://w3id.org/rml/field");

    /**
     * Specifies a data transformation function.
     * <p>Domain: rml:FunctionExecution</p>
     * <p>Range: rdfs:Resource</p>
     */
    public static final OntObjectProperty.Named function = M_MODEL.createObjectProperty("http://w3id.org/rml/function");

    /**
     * Denotes the execution of a function with its required input parameters and values.
     * <p>Domain: rml:ExpressionMap</p>
     * <p>Range: rml:FunctionExecution</p>
     */
    public static final OntObjectProperty.Named functionExecution = M_MODEL.createObjectProperty("http://w3id.org/rml/functionExecution");

    /**
     * A Function Map element to generate the data transformation function.
     * <p>Domain: rml:Input</p>
     * <p>Range: rml:FunctionMap</p>
     */
    public static final OntObjectProperty.Named functionMap = M_MODEL.createObjectProperty("http://w3id.org/rml/functionMap");

    /**
     * Specifies where the terms of a collection or container come from.
     * <p>Domain: rml:GatherMap</p>
     */
    public static final OntObjectProperty.Named gather = M_MODEL.createObjectProperty("http://w3id.org/rml/gather");

    /**
     * Relates a GatherMap with the desired result type of collection or container.
     * <p>Domain: rml:GatherMap</p>
     * <p>Range: _:cc69e20f077cb0cdb405d60e15a17eb1</p>
     */
    public static final OntObjectProperty.Named gatherAs = M_MODEL.createObjectProperty("http://w3id.org/rml/gatherAs");

    /**
     * An IRI reference for use as the graph name of all triples generated with the SubjectMap or PredicateObjectMap.
     * <p>Domain: _:ed6acf41c6a28816e281c7f1ca7c7bd4</p>
     */
    public static final OntObjectProperty.Named graph = M_MODEL.createObjectProperty("http://w3id.org/rml/graph");

    /**
     * Specifies a GraphMap. When used with a SubjectMap element, all the RDF triples generated from a logical row will be stored in the specified named graph. Otherwise, the RDF triple generated using the (predicate, object) pair will be stored in the specified named graph.
     * <p>Domain: _:3e83625ace6f04002105802cfd7272da</p>
     * <p>Range: rml:GraphMap</p>
     */
    public static final OntObjectProperty.Named graphMap = M_MODEL.createObjectProperty("http://w3id.org/rml/graphMap");

    /**
     * An inner join of the logical view.
     * <p>Domain: rml:LogicalView</p>
     * <p>Range: rml:LogicalViewJoin</p>
     */
    public static final OntObjectProperty.Named innerJoin = M_MODEL.createObjectProperty("http://w3id.org/rml/innerJoin");

    /**
     * Specifies the Input element of a Function Map.
     * <p>Domain: rml:FunctionExecution</p>
     * <p>Range: rml:Input</p>
     */
    public static final OntObjectProperty.Named input = M_MODEL.createObjectProperty("http://w3id.org/rml/input");

    /**
     * Specifies the value of a parameter of a function.
     * <p>Domain: rml:Input</p>
     */
    public static final OntObjectProperty.Named inputValue = M_MODEL.createObjectProperty("http://w3id.org/rml/inputValue");

    /**
     * A Value map element to generate the value of a parameter of a function.
     * <p>Domain: rml:Input</p>
     * <p>Range: rml:TermMap</p>
     */
    public static final OntObjectProperty.Named inputValueMap = M_MODEL.createObjectProperty("http://w3id.org/rml/inputValueMap");

    /**
     * Specifies the join condition for joining the child logical source with the parent logical source of the foreign key constraint.
     * <p>Range: rml:Join</p>
     */
    public static final OntObjectProperty.Named joinCondition = M_MODEL.createObjectProperty("http://w3id.org/rml/joinCondition");

    /**
     * A LanguagetMap element to generate the language tag of the object component from a logical source record.
     * <p>Domain: rml:ObjectMap</p>
     * <p>Range: rml:LanguageMap</p>
     */
    public static final OntObjectProperty.Named languageMap = M_MODEL.createObjectProperty("http://w3id.org/rml/languageMap");

    /**
     * A left join of the logical view.
     * <p>Domain: rml:LogicalView</p>
     * <p>Range: rml:LogicalViewJoin</p>
     */
    public static final OntObjectProperty.Named leftJoin = M_MODEL.createObjectProperty("http://w3id.org/rml/leftJoin");

    /**
     * Represents the logical source to be mapped.
     * <p>Domain: rml:TriplesMap</p>
     * <p>Range: rml:AbstractLogicalSource</p>
     */
    public static final OntObjectProperty.Named logicalSource = M_MODEL.createObjectProperty("http://w3id.org/rml/logicalSource");

    /**
     * Represents the logical target to where triples are exported to.
     * <p>Domain: _:23153030ea43462ca449356eeb085c4b</p>
     * <p>Range: rml:LogicalTarget</p>
     */
    public static final OntObjectProperty.Named logicalTarget = M_MODEL.createObjectProperty("http://w3id.org/rml/logicalTarget");

    /**
     * A namespace for describing a XPath reference formulation.
     * <p>Domain: rml:XPathReferenceFormulation</p>
     * <p>Range: rml:Namespace</p>
     */
    public static final OntObjectProperty.Named namespace = M_MODEL.createObjectProperty("http://w3id.org/rml/namespace");

    /**
     * Specifies the object for the generated triple from the logical source record.
     * <p>Domain: rml:PredicateObjectMap</p>
     */
    public static final OntObjectProperty.Named object = M_MODEL.createObjectProperty("http://w3id.org/rml/object");

    /**
     * An ObjectMap element to generate the object component of the (predicate, object) pair from a logical source record.
     * <p>Domain: rml:PredicateObjectMap</p>
     */
    public static final OntObjectProperty.Named objectMap = M_MODEL.createObjectProperty("http://w3id.org/rml/objectMap");

    /**
     * The property onFields specifies the fields in LogicalView that are involved by the structural annotation.           The semantics of this involvement depends on the specific annotation.
     * <p>Domain: rml:StructuralAnnotation</p>
     * <p>Range: rdf:List</p>
     */
    public static final OntObjectProperty.Named onFields = M_MODEL.createObjectProperty("http://w3id.org/rml/onFields");

    /**
     * Specifies the parameter of a function.
     * <p>Domain: rml:Input</p>
     * <p>Range: rdfs:Resource</p>
     */
    public static final OntObjectProperty.Named parameter = M_MODEL.createObjectProperty("http://w3id.org/rml/parameter");

    /**
     * A Parameter Map element to generate the parameter of a function.
     * <p>Domain: rml:Input</p>
     * <p>Range: rml:ParameterMap</p>
     */
    public static final OntObjectProperty.Named parameterMap = M_MODEL.createObjectProperty("http://w3id.org/rml/parameterMap");

    /**
     * The parent logical view of the logical view join.
     * <p>Domain: rml:LogicalViewJoin</p>
     * <p>Range: rml:LogicalView</p>
     */
    public static final OntObjectProperty.Named parentLogicalView = M_MODEL.createObjectProperty("http://w3id.org/rml/parentLogicalView");

    /**
     * Names a parent map in the parent source of a join.
     * <p>Domain: rml:Join</p>
     * <p>Range: rml:ParentMap</p>
     */
    public static final OntObjectProperty.Named parentMap = M_MODEL.createObjectProperty("http://w3id.org/rml/parentMap");

    /**
     * Specifies the TriplesMap element corresponding to the parent logical source of the foreign key constraint.
     * <p>Domain: rml:RefObjectMap</p>
     * <p>Range: rml:TriplesMap</p>
     */
    public static final OntObjectProperty.Named parentTriplesMap = M_MODEL.createObjectProperty("http://w3id.org/rml/parentTriplesMap");

    /**
     * Specifies the predicate for the generated triple from the logical source record.
     * <p>Domain: rml:PredicateObjectMap</p>
     */
    public static final OntObjectProperty.Named predicate = M_MODEL.createObjectProperty("http://w3id.org/rml/predicate");

    /**
     * A PredicateMap element to generate the predicate component of the (predicate, object) pair from a logical source record.
     * <p>Domain: rml:PredicateObjectMap</p>
     * <p>Range: rml:PredicateMap</p>
     */
    public static final OntObjectProperty.Named predicateMap = M_MODEL.createObjectProperty("http://w3id.org/rml/predicateMap");

    /**
     * A PredicateObjectMap element to generate (predicate, object) pair from a logical source record.
     * <p>Domain: rml:TriplesMap</p>
     * <p>Range: rml:PredicateObjectMap</p>
     */
    public static final OntObjectProperty.Named predicateObjectMap = M_MODEL.createObjectProperty("http://w3id.org/rml/predicateObjectMap");

    /**
     * The reference formulation used to refer to extracts of the source data.
     * <p>Domain: rml:Iterable</p>
     * <p>Range: rml:ReferenceFormulation</p>
     */
    public static final OntObjectProperty.Named referenceFormulation = M_MODEL.createObjectProperty("http://w3id.org/rml/referenceFormulation");

    /**
     * Specifies the return output of a function.
     * <p>Domain: rml:ExpressionMap</p>
     * <p>Range: rdfs:Resource</p>
     */
    public static final OntObjectProperty.Named return_ = M_MODEL.createObjectProperty("http://w3id.org/rml/return");

    /**
     * A Return Map element to generate the return output of a function.
     * <p>Domain: rml:ExpressionMap</p>
     * <p>Range: rml:ReturnMap</p>
     */
    public static final OntObjectProperty.Named returnMap = M_MODEL.createObjectProperty("http://w3id.org/rml/returnMap");

    /**
     * Defines the root of the relative path.
     * <p>Domain: _:5f3b1c441e3b2545074e6d095b1baae1</p>
     * <p>Range: _:f013729c1333478141d8e06e98980e87</p>
     */
    public static final OntObjectProperty.Named root = M_MODEL.createObjectProperty("http://w3id.org/rml/root");

    /**
     * Defines the serialization to generate the target output.
     * <p>Domain: rml:LogicalTarget</p>
     * <p>Range: formats:Format</p>
     */
    public static final OntObjectProperty.Named serialization = M_MODEL.createObjectProperty("http://w3id.org/rml/serialization");

    /**
     * Defines the source that describe the input data.
     * <p>Domain: rml:LogicalSource</p>
     * <p>Range: _:c56395119ae23e59f94597e284c065b0</p>
     */
    public static final OntObjectProperty.Named source = M_MODEL.createObjectProperty("http://w3id.org/rml/source");

    /**
     * A Strategy element to indicate how to perform an action.
     * <p>Domain: rml:GatherMap</p>
     * <p>Range: rml:Strategy</p>
     */
    public static final OntObjectProperty.Named strategy = M_MODEL.createObjectProperty("http://w3id.org/rml/strategy");

    /**
     * A structural annotation of the logical view.
     * <p>Domain: rml:LogicalView</p>
     * <p>Range: rml:StructuralAnnotation</p>
     */
    public static final OntObjectProperty.Named structuralAnnotation = M_MODEL.createObjectProperty("http://w3id.org/rml/structuralAnnotation");

    /**
     * An IRI reference for use as subject for all the RDF triples generated from a logical source record.
     * <p>Domain: rml:TriplesMap</p>
     */
    public static final OntObjectProperty.Named subject = M_MODEL.createObjectProperty("http://w3id.org/rml/subject");

    /**
     * A SubjectMap element to generate a subject from a logical source record.
     * <p>Domain: rml:TriplesMap</p>
     * <p>Range: rml:SubjectMap</p>
     */
    public static final OntObjectProperty.Named subjectMap = M_MODEL.createObjectProperty("http://w3id.org/rml/subjectMap");

    /**
     * Defines the target that describe the output data.
     * <p>Domain: rml:LogicalTarget</p>
     * <p>Range: _:1342dcf0d6f31cfe4c847ebb3f200c09</p>
     */
    public static final OntObjectProperty.Named target = M_MODEL.createObjectProperty("http://w3id.org/rml/target");

    /**
     * The property targetFields specifies the Fields target of the InclusionDependencyAnnotation.
     * <p>Domain: rml:StructuralAnnotation</p>
     * <p>Range: rdf:List</p>
     */
    public static final OntObjectProperty.Named targetFields = M_MODEL.createObjectProperty("http://w3id.org/rml/targetFields");

    /**
     * The property targetFields specifies the Fields target of the InclusionDependencyAnnotation.
     * <p>Domain: rml:InclusionDependencyAnnotation</p>
     * <p>Range: rml:LogicalView</p>
     */
    public static final OntObjectProperty.Named targetView = M_MODEL.createObjectProperty("http://w3id.org/rml/targetView");

    /**
     * A string indicating whether subject or object generated using the value from a source record name specified should be an IRI/URI (safe or unsafe) reference, blank node, or a literal.
     * <p>Domain: rml:TermMap</p>
     * <p>Range: _:46486d5d8d85acb384ab0b99877da16a</p>
     */
    public static final OntObjectProperty.Named termType = M_MODEL.createObjectProperty("http://w3id.org/rml/termType");

    /**
     * The source of the logical view. This is the abstract logical source from which the logical view is derived.
     * <p>Domain: rml:LogicalView</p>
     * <p>Range: rml:AbstractLogicalSource</p>
     */
    public static final OntObjectProperty.Named viewOn = M_MODEL.createObjectProperty("http://w3id.org/rml/viewOn");

    /**
     * Indicates the behavior of a GatherMap in case rml:gather does not yield any element
     * <p>Domain: rml:GatherMap</p>
     * <p>Range: xsd:boolean</p>
     */
    public static final OntDataProperty allowEmptyListAndContainer = M_MODEL.createDataProperty("http://w3id.org/rml/allowEmptyListAndContainer");

    /**
     * Names a source record in the child source of a join.
     * <p>Domain: rml:Join</p>
     * <p>Range: xsd:string</p>
     */
    public static final OntDataProperty child = M_MODEL.createDataProperty("http://w3id.org/rml/child");

    /**
     * The name of the field.
     * <p>Domain: rml:Field</p>
     * <p>Range: xsd:string</p>
     */
    public static final OntDataProperty fieldName = M_MODEL.createDataProperty("http://w3id.org/rml/fieldName");

    /**
     * An expression qualified according to the reference formulation that defines a sequence of logical iterations on the data source.
     * <p>Domain: rml:Iterable</p>
     */
    public static final OntDataProperty iterator = M_MODEL.createDataProperty("http://w3id.org/rml/iterator");

    /**
     * Specified the language for the object component for the generated triple.
     * <p>Domain: rml:ObjectMap</p>
     * <p>Range: xsd:string</p>
     */
    public static final OntDataProperty language = M_MODEL.createDataProperty("http://w3id.org/rml/language");

    /**
     * Describes the prefix of a namespace.
     * <p>Domain: rml:Namespace</p>
     * <p>Range: xsd:string</p>
     */
    public static final OntDataProperty namespacePrefix = M_MODEL.createDataProperty("http://w3id.org/rml/namespacePrefix");

    /**
     * Describes the URL of a namespace.
     * <p>Domain: rml:Namespace</p>
     */
    public static final OntDataProperty namespaceURL = M_MODEL.createDataProperty("http://w3id.org/rml/namespaceURL");

    /**
     * Describes which data values inside the source should be considered as NULL.
     * <p>Domain: rml:Source</p>
     * <p>Range: xsd:string</p>
     */
    public static final OntDataProperty null_ = M_MODEL.createDataProperty("http://w3id.org/rml/null");

    /**
     * Names a source record in the parent source of a join.
     * <p>Domain: rml:Join</p>
     * <p>Range: xsd:string</p>
     */
    public static final OntDataProperty parent = M_MODEL.createDataProperty("http://w3id.org/rml/parent");

    /**
     * Denotes the path to a source or target resource.
     * <p>Domain: _:fdf15c0bd1c8740342b4b83635d8974e</p>
     * <p>Range: xsd:string</p>
     */
    public static final OntDataProperty path = M_MODEL.createDataProperty("http://w3id.org/rml/path");

    /**
     * A valid expression that selects values from the source data. The reference should conform to the syntax rules/grammar of the specified Reference Formulation.
     * <p>Domain: rml:ExpressionMap</p>
     * <p>Range: xsd:string</p>
     */
    public static final OntDataProperty reference = M_MODEL.createDataProperty("http://w3id.org/rml/reference");

    /**
     * A template (format string) to specify how to generate a value for a expression map, using one or more records from a logical source record.
     * <p>Domain: rml:ExpressionMap</p>
     * <p>Range: xsd:string</p>
     */
    public static final OntDataProperty template = M_MODEL.createDataProperty("http://w3id.org/rml/template");

    /**
     * A property for indicating whether a expression map is a constant-valued expression map.
     * <p>Domain: rml:ExpressionMap</p>
     */
    public static final OntAnnotationProperty constant = M_MODEL.createAnnotationProperty("http://w3id.org/rml/constant");

    /** An iterable that can be associated with a triples map such that a data source can be mapped to RDF triples. */
    public static final OntClass.Named AbstractLogicalSource = M_MODEL.createOntClass("http://w3id.org/rml/AbstractLogicalSource");

    /** Denotes a blank node, used with termType */
    public static final OntClass.Named BlankNode = M_MODEL.createOntClass("http://w3id.org/rml/BlankNode");

    /** Represents a child map */
    public static final OntClass.Named ChildMap = M_MODEL.createOntClass("http://w3id.org/rml/ChildMap");

    /** Represents file compression schemes. */
    public static final OntClass.Named Compression = M_MODEL.createOntClass("http://w3id.org/rml/Compression");

    /** Denotes the path to the current working directory of the RML processor. */
    public static final OntClass.Named CurrentWorkingDirectory = M_MODEL.createOntClass("http://w3id.org/rml/CurrentWorkingDirectory");

    /** Represents a datatype map */
    public static final OntClass.Named DatatypeMap = M_MODEL.createOntClass("http://w3id.org/rml/DatatypeMap");

    /** Represents encoding formats. */
    public static final OntClass.Named Encoding = M_MODEL.createOntClass("http://w3id.org/rml/Encoding");

    /** An expression field is an expression map that gives a name to an expression. */
    public static final OntClass.Named ExpressionField = M_MODEL.createOntClass("http://w3id.org/rml/ExpressionField");

    /** Represents an expression map */
    public static final OntClass.Named ExpressionMap = M_MODEL.createOntClass("http://w3id.org/rml/ExpressionMap");

    /** A field gives a name to data derived from the abstract logical source on which a logical view is defined. */
    public static final OntClass.Named Field = M_MODEL.createOntClass("http://w3id.org/rml/Field");

    /** Denotes a file's path location. */
    public static final OntClass.Named FilePath = M_MODEL.createOntClass("http://w3id.org/rml/FilePath");

    /** A foreign key annotation is analogous to the notion of foreign key constraint in databases. */
    public static final OntClass.Named ForeignKeyAnnotation = M_MODEL.createOntClass("http://w3id.org/rml/ForeignKeyAnnotation");

    /** Represents a function execution. */
    public static final OntClass.Named FunctionExecution = M_MODEL.createOntClass("http://w3id.org/rml/FunctionExecution");

    /** Represents a function map. */
    public static final OntClass.Named FunctionMap = M_MODEL.createOntClass("http://w3id.org/rml/FunctionMap");

    /** Denotes a term map that generates a collection (rdf:List) or container (rdf:Bag, rdf:Seq, rdf:Alt). */
    public static final OntClass.Named GatherMap = M_MODEL.createOntClass("http://w3id.org/rml/GatherMap");

    /** Represents a graph map. */
    public static final OntClass.Named GraphMap = M_MODEL.createOntClass("http://w3id.org/rml/GraphMap");

    /** Denotes an IRI, used with termType. */
    public static final OntClass.Named IRI = M_MODEL.createOntClass("http://w3id.org/rml/IRI");

    /** A IRI safe annotation indicates that the field content is IRI safe. */
    public static final OntClass.Named IRISafeAnnotation = M_MODEL.createOntClass("http://w3id.org/rml/IRISafeAnnotation");

    /** An inclusion dependency annotation is analogous to the notion of inclusion dependency in databases. */
    public static final OntClass.Named InclusionDependencyAnnotation = M_MODEL.createOntClass("http://w3id.org/rml/InclusionDependencyAnnotation");

    /** Represents a input for an execution. */
    public static final OntClass.Named Input = M_MODEL.createOntClass("http://w3id.org/rml/Input");

    /** An abstract construct to describe data access and iteration for a data source. */
    public static final OntClass.Named Iterable = M_MODEL.createOntClass("http://w3id.org/rml/Iterable");

    /** An iterable field is an iterable that gives a name to a logical iteration. */
    public static final OntClass.Named IterableField = M_MODEL.createOntClass("http://w3id.org/rml/IterableField");

    /** Represents a join condition. */
    public static final OntClass.Named Join = M_MODEL.createOntClass("http://w3id.org/rml/Join");

    /** Represents a language map */
    public static final OntClass.Named LanguageMap = M_MODEL.createOntClass("http://w3id.org/rml/LanguageMap");

    /** Denotes a Literal, used with termType. */
    public static final OntClass.Named Literal = M_MODEL.createOntClass("http://w3id.org/rml/Literal");

    /** Denotes a logical source. */
    public static final OntClass.Named LogicalSource = M_MODEL.createOntClass("http://w3id.org/rml/LogicalSource");

    /** Denotes a logical target. */
    public static final OntClass.Named LogicalTarget = M_MODEL.createOntClass("http://w3id.org/rml/LogicalTarget");

    /** A logical view (rml:LogicalView) is a type of abstract logical source that is derived from another abstract logical source by defining fields with data from said abstract logical source. */
    public static final OntClass.Named LogicalView = M_MODEL.createOntClass("http://w3id.org/rml/LogicalView");

    /** A logical view join is an operation that adapts the logical iterations of one logical view (the child logical view) with fields from another logical view (the parent logical view). */
    public static final OntClass.Named LogicalViewJoin = M_MODEL.createOntClass("http://w3id.org/rml/LogicalViewJoin");

    /** Denotes the path to the location of the RML mapping. */
    public static final OntClass.Named MappingDirectory = M_MODEL.createOntClass("http://w3id.org/rml/MappingDirectory");

    /** Denotes a namespace. */
    public static final OntClass.Named Namespace = M_MODEL.createOntClass("http://w3id.org/rml/Namespace");

    /** A non nullable annotation is analogous to the notion of NOT NULL constraints in databases. */
    public static final OntClass.Named NotNullAnnotation = M_MODEL.createOntClass("http://w3id.org/rml/NotNullAnnotation");

    /** Represents an object map. */
    public static final OntClass.Named ObjectMap = M_MODEL.createOntClass("http://w3id.org/rml/ObjectMap");

    /** Represents a parameter map. */
    public static final OntClass.Named ParameterMap = M_MODEL.createOntClass("http://w3id.org/rml/ParameterMap");

    /** Represents a parent map */
    public static final OntClass.Named ParentMap = M_MODEL.createOntClass("http://w3id.org/rml/ParentMap");

    /** Represents a predicate map. */
    public static final OntClass.Named PredicateMap = M_MODEL.createOntClass("http://w3id.org/rml/PredicateMap");

    /** Represents a predicate-object map. */
    public static final OntClass.Named PredicateObjectMap = M_MODEL.createOntClass("http://w3id.org/rml/PredicateObjectMap");

    /** A primary key annotation is analogous to the notion of a primary key constraints in databases. */
    public static final OntClass.Named PrimaryKeyAnnotation = M_MODEL.createOntClass("http://w3id.org/rml/PrimaryKeyAnnotation");

    /** Denotes a reference to an object map. */
    public static final OntClass.Named RefObjectMap = M_MODEL.createOntClass("http://w3id.org/rml/RefObjectMap");

    /** Denotes a Reference Formulation. */
    public static final OntClass.Named ReferenceFormulation = M_MODEL.createOntClass("http://w3id.org/rml/ReferenceFormulation");

    /** Denotes a relative path location. */
    public static final OntClass.Named RelativePath = M_MODEL.createOntClass("http://w3id.org/rml/RelativePath");

    /** Represents a return map. */
    public static final OntClass.Named ReturnMap = M_MODEL.createOntClass("http://w3id.org/rml/ReturnMap");

    /** Denotes a source. */
    public static final OntClass.Named Source = M_MODEL.createOntClass("http://w3id.org/rml/Source");

    /** Denotes a strategy to perform an action or process. */
    public static final OntClass.Named Strategy = M_MODEL.createOntClass("http://w3id.org/rml/Strategy");

    /** A structural annotation provide a mechanism to express relationships between logical views, as well as additional information about fields. */
    public static final OntClass.Named StructuralAnnotation = M_MODEL.createOntClass("http://w3id.org/rml/StructuralAnnotation");

    /** Represents a subject map. */
    public static final OntClass.Named SubjectMap = M_MODEL.createOntClass("http://w3id.org/rml/SubjectMap");

    /** Denotes a target. */
    public static final OntClass.Named Target = M_MODEL.createOntClass("http://w3id.org/rml/Target");

    /** A function that generates any number of RDF terms from a logical source record. */
    public static final OntClass.Named TermMap = M_MODEL.createOntClass("http://w3id.org/rml/TermMap");

    /** Represents a triples map. */
    public static final OntClass.Named TriplesMap = M_MODEL.createOntClass("http://w3id.org/rml/TriplesMap");

    /** Denotes a URI, used with termType. */
    public static final OntClass.Named URI = M_MODEL.createOntClass("http://w3id.org/rml/URI");

    /** A unique annotation is analogous to the notion of UNIQUE constraints in databases. . */
    public static final OntClass.Named UniqueAnnotation = M_MODEL.createOntClass("http://w3id.org/rml/UniqueAnnotation");

    /** Denotes an unsafe IRI, used with termType. */
    public static final OntClass.Named UnsafeIRI = M_MODEL.createOntClass("http://w3id.org/rml/UnsafeIRI");

    /** Denotes an unsafe URI, used with termType. */
    public static final OntClass.Named UnsafeURI = M_MODEL.createOntClass("http://w3id.org/rml/UnsafeURI");

    /** Denotes a XPath Reference Formulation. */
    public static final OntClass.Named XPathReferenceFormulation = M_MODEL.createOntClass("http://w3id.org/rml/XPathReferenceFormulation");

    /** Denotes that the CSV file's column names are used for referring to extracts of CSV resources. */
    public static final OntIndividual.Named CSV = ReferenceFormulation.createIndividual("http://w3id.org/rml/CSV");

    /** Denotes the JSONPath reference formulation, used for referring to extracts of JSON sources. */
    public static final OntIndividual.Named JSONPath = ReferenceFormulation.createIndividual("http://w3id.org/rml/JSONPath");

    /** Denotes a query in SQL 2008. */
    public static final OntIndividual.Named SQL2008Query = ReferenceFormulation.createIndividual("http://w3id.org/rml/SQL2008Query");

    /** Denotes a table in SQL 2008. */
    public static final OntIndividual.Named SQL2008Table = ReferenceFormulation.createIndividual("http://w3id.org/rml/SQL2008Table");

    /** Represents the UFT-16 encoding. */
    public static final OntIndividual.Named UTF_16 = Encoding.createIndividual("http://w3id.org/rml/UTF-16");

    /** Represents the UFT-8 encoding. */
    public static final OntIndividual.Named UTF_8 = Encoding.createIndividual("http://w3id.org/rml/UTF-8");

    /** Denotes the XPath reference formulation, used for referring to extracts of XML sources. */
    public static final OntIndividual.Named XPath = ReferenceFormulation.createIndividual("http://w3id.org/rml/XPath");

    /** Informs that the sets of RDF terms generated by each TermMap of the GatherMap are to be appended within the collection or container. */
    public static final OntIndividual.Named append = Strategy.createIndividual("http://w3id.org/rml/append");

    /** Informs that the RDF terms generated by each TermMap of the GatherMap are to be grouped (in the constructed collection or container) by doing a Cartesian product of these terms. */
    public static final OntIndividual.Named cartesianProduct = Strategy.createIndividual("http://w3id.org/rml/cartesianProduct");

    /** Represents GZip file compression. */
    public static final OntIndividual.Named gzip = Compression.createIndividual("http://w3id.org/rml/gzip");

    /** Represents no compression. */
    public static final OntIndividual.Named none = Compression.createIndividual("http://w3id.org/rml/none");

    /** Represents TarGZip file compression. */
    public static final OntIndividual.Named targz = Compression.createIndividual("http://w3id.org/rml/targz");

    /** Represents TarXz file compression. */
    public static final OntIndividual.Named tarxz = Compression.createIndividual("http://w3id.org/rml/tarxz");

    /** Represents Zip file compression. */
    public static final OntIndividual.Named zip = Compression.createIndividual("http://w3id.org/rml/zip");

    public static final Property gatherMap = M_MODEL.createProperty(NS + "gatherMap");

    public static final Property condition = M_MODEL.createProperty(NS + "condition");

    public static final Resource defaultGraph = M_MODEL.createResource(NS + "defaultGraph");

    public static final Resource JoinCondition = M_MODEL.createResource(NS + "JoinCondition");

    public static final Resource list = M_MODEL.createResource(NS + "list");

    public static final Resource RelativePathSource = M_MODEL.createResource(NS + "RelativePathSource");

    public static final Resource noEmpty = M_MODEL.createResource(NS + "noEmpty");

    public static final Resource InputValueMap = M_MODEL.createResource(NS + "InputValueMap");

}
