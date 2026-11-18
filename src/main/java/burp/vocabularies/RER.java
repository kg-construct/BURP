package burp.vocabularies;

import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.OntSpecification;
import org.apache.jena.ontapi.model.*;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import burp.reporting.Severity;
import java.util.List;
import java.util.Map;

/**
 * Vocabulary definitions from src/main/resources/vocabularies/rer.ttl.
 */
@SuppressWarnings("unused")
public class RER {

    private static final OntModel M_MODEL = OntModelFactory.createModel(OntSpecification.OWL2_FULL_MEM);

    public static final String NS = "https://w3id.org/dre/rer#";

    public static String getURI() {
        return NS;
    }

    public static final Resource NAMESPACE = M_MODEL.createResource(NS);

    public static final String VERSION_INFO = "0.0.1";

    static {
        try (var in = RER.class.getClassLoader().getResourceAsStream("vocabularies/rer.ttl")) {
            if (in != null) {
                M_MODEL.read(in, NS, "TURTLE");
            }
        } catch (java.io.IOException ignored) {
        }
    }

    /**
     * Number of generated statements grouped by target (rml:LogicalTarget).
     * <p>Domain: rer:RmlExecutionReport</p>
     * <p>Range: rer:GeneratedStatementsPerTarget</p>
     */
    public static final OntObjectProperty.Named generatedStatementsPerTarget = M_MODEL.createObjectProperty("https://w3id.org/dre/rer#generatedStatementsPerTarget");

    /**
     * Number of generated statements grouped by triples map (rml:TriplesMap), all triples map understood by the processor needs to be present. The sum of those is not necessarily equal to rer:generatedStatements as some RML modules may imply or define behaviours after a triples has been generated.
     * <p>Domain: rer:RmlExecutionReport</p>
     * <p>Range: rer:GeneratedStatementPerTriplesMap</p>
     */
    public static final OntObjectProperty.Named generatedStatementsPerTriplesMap = M_MODEL.createObjectProperty("https://w3id.org/dre/rer#generatedStatementsPerTriplesMap");

    /**
     * Indicates an error that occurred.
     * <p>Domain: rer:RmlExecutionReport</p>
     * <p>Range: rer:Error</p>
     */
    public static final OntObjectProperty.Named hasError = M_MODEL.createObjectProperty("https://w3id.org/dre/rer#hasError");

    /**
     * Indicates a log message.
     * <p>Domain: rer:RmlExecutionReport</p>
     * <p>Range: rer:Log</p>
     */
    public static final OntObjectProperty.Named hasLog = M_MODEL.createObjectProperty("https://w3id.org/dre/rer#hasLog");

    /**
     * Literal fragment of source data causing the issue.
     * <p>Domain: rer:Error</p>
     * <p>Range: rdfs:Literal</p>
     */
    public static final OntObjectProperty.Named hasSourceData = M_MODEL.createObjectProperty("https://w3id.org/dre/rer#hasSourceData");

    /**
     * RDF node in the mapping that is at the source of the issue. Prefer using the more precise rer:mappingStatement when possible.
     * <p>Domain: rer:Error</p>
     * <p>Range: rdfs:Resource</p>
     */
    public static final OntObjectProperty.Named mappingNode = M_MODEL.createObjectProperty("https://w3id.org/dre/rer#mappingNode");

    /**
     * RDF statement(s) in the mapping that are at the source of the issue.                 As soon as a mapping document is available as RDF Graph, statements parts should be used to pinpoint problematic constructs.
     * <p>Domain: rer:Log</p>
     * <p>Range: ptr:StatementPart</p>
     */
    public static final OntObjectProperty.Named mappingStatement = M_MODEL.createObjectProperty("https://w3id.org/dre/rer#mappingStatement");

    /**
     * RML Processor plan node at the source of the issue. Processor specific.
     * <p>Domain: rer:Error</p>
     */
    public static final OntObjectProperty.Named planNode = M_MODEL.createObjectProperty("https://w3id.org/dre/rer#planNode");

    /**
     * The Logical Target for which the statistics are reported.
     * <p>Domain: rer:GeneratedStatementsPerTarget</p>
     * <p>Range: rml:LogicalTarget</p>
     */
    public static final OntObjectProperty.Named target = M_MODEL.createObjectProperty("https://w3id.org/dre/rer#target");

    /**
     * The Triples Map for which the statistics are reported.
     * <p>Domain: rer:GeneratedStatementPerTriplesMap</p>
     * <p>Range: rml:TriplesMap</p>
     */
    public static final OntObjectProperty.Named triplesMap = M_MODEL.createObjectProperty("https://w3id.org/dre/rer#triplesMap");

    /**
     * The allowed term types of the term map.
     * <p>Domain: rer:IncorrectTermType</p>
     * <p>Range: rml:TermType</p>
     */
    public static final OntDataProperty allowedTermTypes = M_MODEL.createDataProperty("https://w3id.org/dre/rer#allowedTermTypes");

    /**
     * Raw data value that violates expectations.
     * <p>Domain: rer:DataError</p>
     * <p>Range: rdfs:Literal</p>
     */
    public static final OntDataProperty data = M_MODEL.createDataProperty("https://w3id.org/dre/rer#data");

    /**
     * Datatype of the offending data value.
     * <p>Domain: rer:DataError</p>
     * <p>Range: rdfs:Datatype</p>
     */
    public static final OntDataProperty dataType = M_MODEL.createDataProperty("https://w3id.org/dre/rer#dataType");

    /**
     * Expected datatype for the offending value.
     * <p>Domain: rer:DatatypeExpectationError</p>
     * <p>Range: rdfs:Datatype</p>
     */
    public static final OntDataProperty expectedDataType = M_MODEL.createDataProperty("https://w3id.org/dre/rer#expectedDataType");

    /**
     * Number of statements produced by the execution of the mapping at a specific scope (total, per Triples Map, per target).
     * <p>Domain: _:6e5346d9658f28ee94d75d868a420f7c</p>
     * <p>Range: xsd:integer</p>
     */
    public static final OntDataProperty generatedStatements = M_MODEL.createDataProperty("https://w3id.org/dre/rer#generatedStatements");

    /**
     * Are the output datasets complete.
     * <p>Domain: rer:RmlExecutionReport</p>
     * <p>Range: xsd:boolean</p>
     */
    public static final OntDataProperty isComplete = M_MODEL.createDataProperty("https://w3id.org/dre/rer#isComplete");

    /**
     * Indicates if the execution completed successfully without critical failure. Typically corresponds to exit code 0.
     * <p>Domain: rer:RmlExecutionReport</p>
     * <p>Range: xsd:boolean</p>
     */
    public static final OntDataProperty isSuccess = M_MODEL.createDataProperty("https://w3id.org/dre/rer#isSuccess");

    /**
     * Serialized data item for the iteration that generated the log message.
     * <p>Domain: rer:Log</p>
     * <p>Range: rdfs:Literal</p>
     */
    public static final OntDataProperty iterationData = M_MODEL.createDataProperty("https://w3id.org/dre/rer#iterationData");

    /**
     * The index of the iteration that generated the log message.
     * <p>Domain: rer:Log</p>
     * <p>Range: xsd:integer</p>
     */
    public static final OntDataProperty iterationIndex = M_MODEL.createDataProperty("https://w3id.org/dre/rer#iterationIndex");

    /**
     * Language tag attached to the offending literal.
     * <p>Domain: rer:InvalidLanguageTagError</p>
     * <p>Range: xsd:language</p>
     */
    public static final OntDataProperty languageTag = M_MODEL.createDataProperty("https://w3id.org/dre/rer#languageTag");

    /**
     * Human-readable message associated with an error or a warning.
     * <p>Domain: rer:Log</p>
     * <p>Range: rdfs:Literal</p>
     */
    public static final OntDataProperty message = M_MODEL.createDataProperty("https://w3id.org/dre/rer#message");

    /**
     * Indicate how many Triples Map were found in the RML mapping document.
     * <p>Domain: rer:RmlExecutionReport</p>
     * <p>Range: xsd:integer</p>
     */
    public static final OntDataProperty numberOfTriplesMap = M_MODEL.createDataProperty("https://w3id.org/dre/rer#numberOfTriplesMap");

    /**
     * An optional implementation defined order for log messages.
     * <p>Domain: rer:Log</p>
     */
    public static final OntDataProperty order = M_MODEL.createDataProperty("https://w3id.org/dre/rer#order");

    /**
     * The name of the RML processor that generated this report.
     * <p>Domain: rer:RmlExecutionReport</p>
     * <p>Range: xsd:string</p>
     */
    public static final OntDataProperty processorName = M_MODEL.createDataProperty("https://w3id.org/dre/rer#processorName");

    /**
     * The version of the RML processor that generated this report.
     * <p>Domain: rer:RmlExecutionReport</p>
     * <p>Range: xsd:string</p>
     */
    public static final OntDataProperty processorVersion = M_MODEL.createDataProperty("https://w3id.org/dre/rer#processorVersion");

    /**
     * The reference formulation expression that generated the log message.
     * <p>Domain: rer:ReferenceFormulationError</p>
     * <p>Range: xsd:string</p>
     */
    public static final OntDataProperty reference = M_MODEL.createDataProperty("https://w3id.org/dre/rer#reference");

    /**
     * The reference formulation used in the mapping that generated the log message.
     * <p>Domain: rer:ReferenceFormulationError</p>
     * <p>Range: rml:ReferenceFormulation</p>
     */
    public static final OntDataProperty referenceFormulation = M_MODEL.createDataProperty("https://w3id.org/dre/rer#referenceFormulation");

    /**
     * The stack trace of the error that generated the log message.
     * <p>Domain: rer:Log</p>
     * <p>Range: xsd:string</p>
     */
    public static final OntDataProperty stackTrace = M_MODEL.createDataProperty("https://w3id.org/dre/rer#stackTrace");

    /**
     * Time at which the log entry was produced.
     * <p>Domain: rer:Log</p>
     * <p>Range: xsd:dateTimeStamp</p>
     */
    public static final OntDataProperty timestamp = M_MODEL.createDataProperty("https://w3id.org/dre/rer#timestamp");

    /**
     * Associates a SHACL shape or constraint with a specific RER error class.
     * <p>Range: owl:Class</p>
     */
    public static final OntAnnotationProperty error = M_MODEL.createAnnotationProperty("https://w3id.org/dre/rer#error");

    /**
     * Hypothesis on what may have caused the log entry.
     * <p>Domain: rer:Log</p>
     * <p>Range: rdfs:Literal</p>
     */
    public static final OntAnnotationProperty potentialCause = M_MODEL.createAnnotationProperty("https://w3id.org/dre/rer#potentialCause");

    /**
     * Potential confusion with other errors classes.
     * <p>Domain: rer:Log</p>
     * <p>Range: xsd:string</p>
     */
    public static final OntAnnotationProperty potentialConfusion = M_MODEL.createAnnotationProperty("https://w3id.org/dre/rer#potentialConfusion");

    /**
     * Potential confusion with other errors classes.
     * <p>Domain: rer:Log</p>
     * <p>Range: rer:Log</p>
     */
    public static final OntAnnotationProperty potentialConfusionError = M_MODEL.createAnnotationProperty("https://w3id.org/dre/rer#potentialConfusionError");

    /**
     * Suggested actions to resolve or investigate the log entry.
     * <p>Domain: rer:Log</p>
     * <p>Range: rdfs:Literal</p>
     */
    public static final OntAnnotationProperty potentialSolution = M_MODEL.createAnnotationProperty("https://w3id.org/dre/rer#potentialSolution");

    /**
     * Context resources recommended to be attache to the error entry log.Used for example to generate an error message.
     * <p>Domain: rer:Log</p>
     * <p>Range: rdfs:Resource</p>
     */
    public static final OntAnnotationProperty suggestedContext = M_MODEL.createAnnotationProperty("https://w3id.org/dre/rer#suggestedContext");

    /** The mapping document cannot be read (e.g. missing file, insufficient permissions, unreadable stream). */
    public static final OntClass.Named CannotReadMappingDocument = M_MODEL.createOntClass("https://w3id.org/dre/rer#CannotReadMappingDocument");

    /** Errors related to engine or environment configuration. */
    public static final OntClass.Named ConfigurationError = M_MODEL.createOntClass("https://w3id.org/dre/rer#ConfigurationError");

    /** The obtained data is problematic. */
    public static final OntClass.Named DataError = M_MODEL.createOntClass("https://w3id.org/dre/rer#DataError");

    /** Errors where a value violates an expected datatype. */
    public static final OntClass.Named DatatypeExpectationError = M_MODEL.createOntClass("https://w3id.org/dre/rer#DatatypeExpectationError");

    /** The mapping uses a deprecated RML feature. */
    public static final OntClass.Named DeprecatedFeature = M_MODEL.createOntClass("https://w3id.org/dre/rer#DeprecatedFeature");

    /** A parameter is bound multiple times for the same function execution. */
    public static final OntClass.Named DuplicateParameter = M_MODEL.createOntClass("https://w3id.org/dre/rer#DuplicateParameter");

    /** An error that occurred during the RML processor execution. */
    public static final OntClass.Named Error = M_MODEL.createOntClass("https://w3id.org/dre/rer#Error");

    /** Errors related to the execution of a mapping (plan) with real data */
    public static final OntClass.Named ExecutionError = M_MODEL.createOntClass("https://w3id.org/dre/rer#ExecutionError");

    /** An error that occurs while executing a function during mapping evaluation. */
    public static final OntClass.Named FunctionExecutionError = M_MODEL.createOntClass("https://w3id.org/dre/rer#FunctionExecutionError");

    /** Statistics about the number of generated statements for a specific Triples Map. */
    public static final OntClass.Named GeneratedStatementPerTriplesMap = M_MODEL.createOntClass("https://w3id.org/dre/rer#GeneratedStatementPerTriplesMap");

    /** Statistics about the number of generated statements for a specific Logical Target. */
    public static final OntClass.Named GeneratedStatementsPerTarget = M_MODEL.createOntClass("https://w3id.org/dre/rer#GeneratedStatementsPerTarget");

    /** The value of a typed literal is not valid according to the datatype. */
    public static final OntClass.Named IllTypedLiteralError = M_MODEL.createOntClass("https://w3id.org/dre/rer#IllTypedLiteralError");

    /** The term type of a term is not the expected one. */
    public static final OntClass.Named IncorrectTermType = M_MODEL.createOntClass("https://w3id.org/dre/rer#IncorrectTermType");

    /** A information from the RML processor. */
    public static final OntClass.Named Information = M_MODEL.createOntClass("https://w3id.org/dre/rer#Information");

    /** An error indicating that a generated IRI is syntactically invalid [RFC3987] or not allowed. */
    public static final OntClass.Named InvalidIRI = M_MODEL.createOntClass("https://w3id.org/dre/rer#InvalidIRI");

    /** The language tag of a literal is not valid according to the BCP47 specification. */
    public static final OntClass.Named InvalidLanguageTagError = M_MODEL.createOntClass("https://w3id.org/dre/rer#InvalidLanguageTagError");

    /** An error indicating that the produced RDF graph is invalid. */
    public static final OntClass.Named InvalidOutputRDF = M_MODEL.createOntClass("https://w3id.org/dre/rer#InvalidOutputRDF");

    /** A parameter is not a recognized parameter for the specified function. */
    public static final OntClass.Named InvalidParameter = M_MODEL.createOntClass("https://w3id.org/dre/rer#InvalidParameter");

    /** An error indicating that a generated URI is syntactically invalid [RFC3986] or not allowed. */
    public static final OntClass.Named InvalidURI = M_MODEL.createOntClass("https://w3id.org/dre/rer#InvalidURI");

    /** An error indicating that a query or logical source iterator expression has invalid syntax. */
    public static final OntClass.Named IteratorSyntaxError = M_MODEL.createOntClass("https://w3id.org/dre/rer#IteratorSyntaxError");

    /** A log message produced during RML processor execution, look at subClasses for the gravity definition. (Information < Warning < Error) */
    public static final OntClass.Named Log = M_MODEL.createOntClass("https://w3id.org/dre/rer#Log");

    /** Error while obtaining an iteration or iterating over a source. */
    public static final OntClass.Named LogicalSourceError = M_MODEL.createOntClass("https://w3id.org/dre/rer#LogicalSourceError");

    /** Errors related to the mapping definition itself. Changing input data or configuration will not make it work. */
    public static final OntClass.Named MappingError = M_MODEL.createOntClass("https://w3id.org/dre/rer#MappingError");

    /** A required library, plugin, external tool or service is not available at runtime. */
    public static final OntClass.Named MissingDependency = M_MODEL.createOntClass("https://w3id.org/dre/rer#MissingDependency");

    /** A required parameter is missing from the function execution. */
    public static final OntClass.Named MissingParameter = M_MODEL.createOntClass("https://w3id.org/dre/rer#MissingParameter");

    /** No triples map found in the graph. */
    public static final OntClass.Named NoTriplesMap = M_MODEL.createOntClass("https://w3id.org/dre/rer#NoTriplesMap");

    /** The language tag attached to a literal does not conform to BCP47 syntax (malformed tag). */
    public static final OntClass.Named NotWellFormedLanguageTagError = M_MODEL.createOntClass("https://w3id.org/dre/rer#NotWellFormedLanguageTagError");

    /** Object datatype violates declared rdfs:range in the target ontology. */
    public static final OntClass.Named OntologyDatatypeMismatch = M_MODEL.createOntClass("https://w3id.org/dre/rer#OntologyDatatypeMismatch");

    /** Predicate is not declared for the subject class or its superclasses in the target ontology. */
    public static final OntClass.Named OntologyDomainMismatch = M_MODEL.createOntClass("https://w3id.org/dre/rer#OntologyDomainMismatch");

    /** Term type is incompatible with ontology property kind (e.g. literal used for owl:ObjectProperty). */
    public static final OntClass.Named OntologyTermTypeMismatch = M_MODEL.createOntClass("https://w3id.org/dre/rer#OntologyTermTypeMismatch");

    /** Warnings related to target ontology conformance. */
    public static final OntClass.Named OntologyWarning = M_MODEL.createOntClass("https://w3id.org/dre/rer#OntologyWarning");

    /** The engine ran out of memory while executing a mapping or producing results. */
    public static final OntClass.Named OutOfMemory = M_MODEL.createOntClass("https://w3id.org/dre/rer#OutOfMemory");

    /** The mapping contains constructs that that violate the RML specification (e.g., two logical sources), regardless of the engine. */
    public static final OntClass.Named OutOfSpec = M_MODEL.createOntClass("https://w3id.org/dre/rer#OutOfSpec");

    /** Errors related to function parameters in the mapping. */
    public static final OntClass.Named ParameterError = M_MODEL.createOntClass("https://w3id.org/dre/rer#ParameterError");

    /** An error indicating that a predicate used in the mapping cannot be reified as required. */
    public static final OntClass.Named PredicateNotReifiable = M_MODEL.createOntClass("https://w3id.org/dre/rer#PredicateNotReifiable");

    /** Error in the RDF Serialization of the mapping. */
    public static final OntClass.Named RDFMappingSyntaxError = M_MODEL.createOntClass("https://w3id.org/dre/rer#RDFMappingSyntaxError");

    /** Errors related to the reference formulation used in the mapping. */
    public static final OntClass.Named ReferenceFormulationError = M_MODEL.createOntClass("https://w3id.org/dre/rer#ReferenceFormulationError");

    /** Failed to extract a value from the source data using the provided reference formulation. */
    public static final OntClass.Named ReferenceFormulationExecutionError = M_MODEL.createOntClass("https://w3id.org/dre/rer#ReferenceFormulationExecutionError");

    /** An error indicating that the reference formulation expression in the mapping has invalid syntax. */
    public static final OntClass.Named ReferenceFormulationSyntaxError = M_MODEL.createOntClass("https://w3id.org/dre/rer#ReferenceFormulationSyntaxError");

    /** A report generated by an RML Processor. */
    public static final OntClass.Named RmlExecutionReport = M_MODEL.createOntClass("https://w3id.org/dre/rer#RmlExecutionReport");

    /** The specified rml:Source cannot be accessed/reached/does not exist. */
    public static final OntClass.Named SourceAccessError = M_MODEL.createOntClass("https://w3id.org/dre/rer#SourceAccessError");

    /** Errors related to the parsing of a mapping definition. */
    public static final OntClass.Named SyntaxError = M_MODEL.createOntClass("https://w3id.org/dre/rer#SyntaxError");

    /** The template string has invalid syntax (e.g. unclosed or nested braces, empty expressions). */
    public static final OntClass.Named TemplateSyntaxError = M_MODEL.createOntClass("https://w3id.org/dre/rer#TemplateSyntaxError");

    /** The type of a data could not be converted to the target type. */
    public static final OntClass.Named TypeConversionError = M_MODEL.createOntClass("https://w3id.org/dre/rer#TypeConversionError");

    /** A generic internal engine error, typically indicating a bug or unexpected failure not attributable to user input. */
    public static final OntClass.Named UnexpectedError = M_MODEL.createOntClass("https://w3id.org/dre/rer#UnexpectedError");

    /** The mapping references a function that is not known or not implemented. */
    public static final OntClass.Named UnknownFunction = M_MODEL.createOntClass("https://w3id.org/dre/rer#UnknownFunction");

    /** The parameter uses a predicate IRI or unrecognized parameter IRI instead of the parameter definition IRI. */
    public static final OntClass.Named UnknownFunctionParameterIRI = M_MODEL.createOntClass("https://w3id.org/dre/rer#UnknownFunctionParameterIRI");

    /** The return map uses an output predicate IRI instead of the return definition IRI. */
    public static final OntClass.Named UnknownFunctionReturnIRI = M_MODEL.createOntClass("https://w3id.org/dre/rer#UnknownFunctionReturnIRI");

    /** The obtained data does not follow the expected format. */
    public static final OntClass.Named UnparseableDataError = M_MODEL.createOntClass("https://w3id.org/dre/rer#UnparseableDataError");

    /** The engine encountered a property or class in the RML namespace it does not recognize. */
    public static final OntClass.Named UnrecognizedTerm = M_MODEL.createOntClass("https://w3id.org/dre/rer#UnrecognizedTerm");

    /** The mapping references a function that the processor cannot execute because the implementation logic is missing. */
    public static final OntClass.Named UnsupportedFunction = M_MODEL.createOntClass("https://w3id.org/dre/rer#UnsupportedFunction");

    /** The processor encounters a feature, configuration, or term that it cannot execute. The processor implies no judgment on the validity of the construct, only its own inability to process it. */
    public static final OntClass.Named UnsupportedMapping = M_MODEL.createOntClass("https://w3id.org/dre/rer#UnsupportedMapping");

    /** An RML element is present in the mapping but not used by the RML Processor. */
    public static final OntClass.Named UnusedRmlElement = M_MODEL.createOntClass("https://w3id.org/dre/rer#UnusedRmlElement");

    /** A warning that occurred during the RML processor execution. */
    public static final OntClass.Named Warning = M_MODEL.createOntClass("https://w3id.org/dre/rer#Warning");

    public static final Map<String, List<String>> SUPER_CLASSES = Map.ofEntries(
            Map.entry("https://w3id.org/dre/rer#CannotReadMappingDocument", List.of("https://w3id.org/dre/rer#ConfigurationError", "https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#ConfigurationError", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#DataError", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#ExecutionError", "https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#DatatypeExpectationError", List.of("https://w3id.org/dre/rer#DataError", "https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#ExecutionError", "https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#DeprecatedFeature", List.of("https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#Warning")),
            Map.entry("https://w3id.org/dre/rer#DuplicateParameter", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#MappingError", "https://w3id.org/dre/rer#ParameterError")),
            Map.entry("https://w3id.org/dre/rer#Error", List.of("https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#ExecutionError", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#FunctionExecutionError", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#ExecutionError", "https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#GeneratedStatementPerTriplesMap", List.of()),
            Map.entry("https://w3id.org/dre/rer#GeneratedStatementsPerTarget", List.of()),
            Map.entry("https://w3id.org/dre/rer#IllTypedLiteralError", List.of("https://w3id.org/dre/rer#DataError", "https://w3id.org/dre/rer#DatatypeExpectationError", "https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#ExecutionError", "https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#IncorrectTermType", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#MappingError", "https://w3id.org/dre/rer#OutOfSpec")),
            Map.entry("https://w3id.org/dre/rer#Information", List.of("https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#InvalidIRI", List.of("https://w3id.org/dre/rer#DataError", "https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#ExecutionError", "https://w3id.org/dre/rer#InvalidOutputRDF", "https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#InvalidLanguageTagError", List.of("https://w3id.org/dre/rer#DataError", "https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#ExecutionError", "https://w3id.org/dre/rer#InvalidOutputRDF", "https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#InvalidOutputRDF", List.of("https://w3id.org/dre/rer#DataError", "https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#ExecutionError", "https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#InvalidParameter", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#MappingError", "https://w3id.org/dre/rer#ParameterError")),
            Map.entry("https://w3id.org/dre/rer#InvalidURI", List.of("https://w3id.org/dre/rer#DataError", "https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#ExecutionError", "https://w3id.org/dre/rer#InvalidOutputRDF", "https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#IteratorSyntaxError", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#ExecutionError", "https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#LogicalSourceError", "https://w3id.org/dre/rer#MappingError", "https://w3id.org/dre/rer#ReferenceFormulationError", "https://w3id.org/dre/rer#ReferenceFormulationSyntaxError", "https://w3id.org/dre/rer#SyntaxError")),
            Map.entry("https://w3id.org/dre/rer#Log", List.of()),
            Map.entry("https://w3id.org/dre/rer#LogicalSourceError", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#ExecutionError", "https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#MappingError", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#MissingDependency", List.of("https://w3id.org/dre/rer#ConfigurationError", "https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#MissingParameter", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#MappingError", "https://w3id.org/dre/rer#ParameterError")),
            Map.entry("https://w3id.org/dre/rer#NoTriplesMap", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#MappingError")),
            Map.entry("https://w3id.org/dre/rer#NotWellFormedLanguageTagError", List.of("https://w3id.org/dre/rer#DataError", "https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#ExecutionError", "https://w3id.org/dre/rer#InvalidLanguageTagError", "https://w3id.org/dre/rer#InvalidOutputRDF", "https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#OntologyDatatypeMismatch", List.of("https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#OntologyWarning", "https://w3id.org/dre/rer#Warning")),
            Map.entry("https://w3id.org/dre/rer#OntologyDomainMismatch", List.of("https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#OntologyWarning", "https://w3id.org/dre/rer#Warning")),
            Map.entry("https://w3id.org/dre/rer#OntologyTermTypeMismatch", List.of("https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#OntologyWarning", "https://w3id.org/dre/rer#Warning")),
            Map.entry("https://w3id.org/dre/rer#OntologyWarning", List.of("https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#Warning")),
            Map.entry("https://w3id.org/dre/rer#OutOfMemory", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#ExecutionError", "https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#OutOfSpec", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#MappingError")),
            Map.entry("https://w3id.org/dre/rer#ParameterError", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#MappingError")),
            Map.entry("https://w3id.org/dre/rer#PredicateNotReifiable", List.of("https://w3id.org/dre/rer#DataError", "https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#ExecutionError", "https://w3id.org/dre/rer#InvalidOutputRDF", "https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#RDFMappingSyntaxError", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#MappingError", "https://w3id.org/dre/rer#SyntaxError")),
            Map.entry("https://w3id.org/dre/rer#ReferenceFormulationError", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#ReferenceFormulationExecutionError", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#ExecutionError", "https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#ReferenceFormulationError")),
            Map.entry("https://w3id.org/dre/rer#ReferenceFormulationSyntaxError", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#MappingError", "https://w3id.org/dre/rer#ReferenceFormulationError", "https://w3id.org/dre/rer#SyntaxError")),
            Map.entry("https://w3id.org/dre/rer#RmlExecutionReport", List.of()),
            Map.entry("https://w3id.org/dre/rer#SourceAccessError", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#ExecutionError", "https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#SyntaxError", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#TemplateSyntaxError", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#MappingError", "https://w3id.org/dre/rer#SyntaxError")),
            Map.entry("https://w3id.org/dre/rer#TypeConversionError", List.of("https://w3id.org/dre/rer#DataError", "https://w3id.org/dre/rer#DatatypeExpectationError", "https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#ExecutionError", "https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#UnexpectedError", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#Log")),
            Map.entry("https://w3id.org/dre/rer#UnknownFunction", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#MappingError", "https://w3id.org/dre/rer#UnsupportedFunction", "https://w3id.org/dre/rer#UnsupportedMapping")),
            Map.entry("https://w3id.org/dre/rer#UnknownFunctionParameterIRI", List.of("https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#Warning")),
            Map.entry("https://w3id.org/dre/rer#UnknownFunctionReturnIRI", List.of("https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#Warning")),
            Map.entry("https://w3id.org/dre/rer#UnparseableDataError", List.of("https://w3id.org/dre/rer#DataError", "https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#ExecutionError", "https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#SyntaxError")),
            Map.entry("https://w3id.org/dre/rer#UnrecognizedTerm", List.of("https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#Warning")),
            Map.entry("https://w3id.org/dre/rer#UnsupportedFunction", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#MappingError", "https://w3id.org/dre/rer#UnsupportedMapping")),
            Map.entry("https://w3id.org/dre/rer#UnsupportedMapping", List.of("https://w3id.org/dre/rer#Error", "https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#MappingError")),
            Map.entry("https://w3id.org/dre/rer#UnusedRmlElement", List.of("https://w3id.org/dre/rer#Log", "https://w3id.org/dre/rer#Warning")),
            Map.entry("https://w3id.org/dre/rer#Warning", List.of("https://w3id.org/dre/rer#Log"))
    );

    public static final Map<String, Severity> SEVERITIES = Map.ofEntries(
            Map.entry("https://w3id.org/dre/rer#CannotReadMappingDocument", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#ConfigurationError", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#DataError", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#DatatypeExpectationError", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#DeprecatedFeature", Severity.WARNING),
            Map.entry("https://w3id.org/dre/rer#DuplicateParameter", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#Error", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#ExecutionError", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#FunctionExecutionError", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#IllTypedLiteralError", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#IncorrectTermType", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#Information", Severity.INFORMATION),
            Map.entry("https://w3id.org/dre/rer#InvalidIRI", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#InvalidLanguageTagError", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#InvalidOutputRDF", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#InvalidParameter", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#InvalidURI", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#IteratorSyntaxError", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#LogicalSourceError", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#MappingError", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#MissingDependency", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#MissingParameter", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#NoTriplesMap", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#NotWellFormedLanguageTagError", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#OntologyDatatypeMismatch", Severity.WARNING),
            Map.entry("https://w3id.org/dre/rer#OntologyDomainMismatch", Severity.WARNING),
            Map.entry("https://w3id.org/dre/rer#OntologyTermTypeMismatch", Severity.WARNING),
            Map.entry("https://w3id.org/dre/rer#OntologyWarning", Severity.WARNING),
            Map.entry("https://w3id.org/dre/rer#OutOfMemory", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#OutOfSpec", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#ParameterError", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#PredicateNotReifiable", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#RDFMappingSyntaxError", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#ReferenceFormulationError", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#ReferenceFormulationExecutionError", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#ReferenceFormulationSyntaxError", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#SourceAccessError", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#SyntaxError", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#TemplateSyntaxError", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#TypeConversionError", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#UnexpectedError", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#UnknownFunction", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#UnknownFunctionParameterIRI", Severity.WARNING),
            Map.entry("https://w3id.org/dre/rer#UnknownFunctionReturnIRI", Severity.WARNING),
            Map.entry("https://w3id.org/dre/rer#UnparseableDataError", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#UnrecognizedTerm", Severity.WARNING),
            Map.entry("https://w3id.org/dre/rer#UnsupportedFunction", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#UnsupportedMapping", Severity.ERROR),
            Map.entry("https://w3id.org/dre/rer#UnusedRmlElement", Severity.WARNING),
            Map.entry("https://w3id.org/dre/rer#Warning", Severity.WARNING)
    );

    public static Severity severityOf(OntClass errorType) {
        return SEVERITIES.getOrDefault(errorType.getURI(), Severity.ERROR);
    }

    public static List<String> superClassesOf(OntClass errorType) {
        return SUPER_CLASSES.getOrDefault(errorType.getURI(), List.of());
    }

}
