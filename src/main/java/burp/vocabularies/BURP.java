package burp.vocabularies;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

public class BURP {
    public static final String NS = "http://BURP.noname/";
    public static final String base_uri = NS;

    private static Property property(String local) {
        return ResourceFactory.createProperty(NS + local);
    }
    private static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }

    public static final Property list = property("list");
    public static final Property noEmpty = property("noEmpty");

    public static final Resource LogicalView = resource("LogicalView");
    public static final Resource CollectionOrContainer = resource("CollectionOrContainer");

    public static final Property TOKEN = property("token");
    public static final Property FILE_ID = property("file");
    public static final Property START_LINE = property("startLine");
    public static final Property START_COLUMN = property("startColumn");
    public static final Property END_LINE = property("endLine");
    public static final Property END_COLUMN = property("endColumn");

    public static final Property STRING_START_LINE = property("startLine");
    public static final Property STRING_START_COLUMN = property("startColumn");
    public static final Property STRING_END_LINE = property("endLine");
    public static final Property STRING_END_COLUMN = property("endColumn");

    public static final Property BLANK_NODE_ID = property("blankNodeId");

    public static final Resource SUBJECT = resource("SubjectProv");
    public static final Resource PREDICATE = resource("PredicateProv");
    public static final Resource OBJECT = resource("ObjectProv");

    public static final Resource TestTurtleAnnotation = resource("TestTurtleAnnotation");
}
