package burp.vocabularies;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/// Hand-managed W3C Formats vocabulary constants used for SPARQL result formulations.
public final class Formats {

    public static final String NS = "http://www.w3.org/ns/formats/";

    public static final Resource SPARQL_Results_CSV = ResourceFactory.createResource(NS + "SPARQL_Results_CSV");
    public static final Resource SPARQL_Results_TSV = ResourceFactory.createResource(NS + "SPARQL_Results_TSV");
    public static final Resource SPARQL_Results_JSON = ResourceFactory.createResource(NS + "SPARQL_Results_JSON");
    public static final Resource SPARQL_Results_XML = ResourceFactory.createResource(NS + "SPARQL_Results_XML");

    private Formats() {
    }
}
