package burp.vocabularies;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;


public final class YANG {

	public static final String NS = "https://w3id.org/yang/server#";

    public static final Resource Datastore = ResourceFactory.createResource(NS + "Datastore");
    public static final Resource OperationalDatastore = ResourceFactory.createResource(NS + "OperationalDatastore");
    public static final Resource DynamicDatastore = ResourceFactory.createResource(NS + "DynamicDatastore");
    public static final Resource ConventionalDatastore = ResourceFactory.createResource(NS + "ConventionalDatastore");
    public static final Resource RunningDatastore = ResourceFactory.createResource(NS + "RunningDatastore");
    public static final Resource CandidateDatastore = ResourceFactory.createResource(NS + "CandidateDatastore");
    public static final Resource IntendedDatastore = ResourceFactory.createResource(NS + "IntendedDatastore");
    public static final Resource StartupDatastore = ResourceFactory.createResource(NS + "StartupDatastore");

    public static final Resource YangServer = ResourceFactory.createResource(NS + "YangServer");
    public static final Property endpoint = ResourceFactory.createProperty(NS + "endpoint");
	public static final Property username = ResourceFactory.createProperty(NS + "username");
	public static final Property password = ResourceFactory.createProperty(NS + "password");

    public static final Resource NetconfServer = ResourceFactory.createResource(NS + "NetconfServer");

    public static final Resource Query = ResourceFactory.createResource(NS + "Query");
    public static final Resource Subscription = ResourceFactory.createResource(NS + "Subscription");
    public static final Property sourceServer = ResourceFactory.createProperty(NS + "sourceServer");
    public static final Property sourceDatastore = ResourceFactory.createProperty(NS + "sourceDatastore");

    public static final Resource Filter = ResourceFactory.createResource(NS + "Filter");
    public static final Resource SubtreeFilter = ResourceFactory.createResource(NS + "SubtreeFilter");
    public static final Property filter = ResourceFactory.createProperty(NS + "filter");
    public static final Property subtreeValue = ResourceFactory.createProperty(NS + "subtreeValue");
}
