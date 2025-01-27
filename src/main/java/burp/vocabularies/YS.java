package burp.vocabularies;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;


public final class YS {

	public static final String NS = "https://w3id.org/yang/server#";

    // YANG Server Core
    public static final Resource Datastore = ResourceFactory.createResource(NS + "Datastore");
    public static final Property server = ResourceFactory.createProperty(NS + "server");

    public static final Resource OperationalDatastore = ResourceFactory.createResource(NS + "OperationalDatastore");
    public static final Resource DynamicDatastore = ResourceFactory.createResource(NS + "DynamicDatastore");
    public static final Resource ConventionalDatastore = ResourceFactory.createResource(NS + "ConventionalDatastore");
    public static final Resource RunningDatastore = ResourceFactory.createResource(NS + "RunningDatastore");
    public static final Resource CandidateDatastore = ResourceFactory.createResource(NS + "CandidateDatastore");
    public static final Resource IntendedDatastore = ResourceFactory.createResource(NS + "IntendedDatastore");
    public static final Resource StartupDatastore = ResourceFactory.createResource(NS + "StartupDatastore");

    public static final Resource YangServer = ResourceFactory.createResource(NS + "YangServer");
    public static final Property socketAddress = ResourceFactory.createProperty(NS + "socketAddress");
    public static final Property serverAccount = ResourceFactory.createProperty(NS + "serverAccount");
    public static final Resource ServerAccount = ResourceFactory.createResource(NS + "ServerAccount");
    public static final Property username = ResourceFactory.createProperty(NS + "username");

    // NETCONF
    public static final Resource NetconfServer = ResourceFactory.createResource(NS + "NetconfServer");
    public static final Property hostKeyVerification = ResourceFactory.createProperty(NS + "hostKeyVerification");

    // YANG Operations
    public static final Resource Operation = ResourceFactory.createResource(NS + "Operation");
    public static final Resource Query = ResourceFactory.createResource(NS + "Query");
    public static final Property sourceDatastore = ResourceFactory.createProperty(NS + "sourceDatastore");
    public static final Property filter = ResourceFactory.createProperty(NS + "filter");
    public static final Resource SubtreeFilter = ResourceFactory.createResource(NS + "SubtreeFilter");
    public static final Property subtreeValue = ResourceFactory.createProperty(NS + "subtreeValue");
    public static final Resource XPathFilter = ResourceFactory.createResource(NS + "XPathFilter");
    public static final Property xpathValue = ResourceFactory.createProperty(NS + "xpathValue");
    public static final Property namespace = ResourceFactory.createProperty(NS + "namespace");
    public static final Resource Namespace = ResourceFactory.createResource(NS + "Namespace");
    public static final Property namespacePrefix = ResourceFactory.createProperty(NS + "namespacePrefix");
    public static final Property namespaceURL = ResourceFactory.createProperty(NS + "namespaceURL");

    // RML alignment
    public static final Resource NetconfQuerySource = ResourceFactory.createResource(NS + "NetconfQuerySource");
}
