package burp.model;

import org.apache.jena.rdf.model.Resource;

public class LogicalTarget {
    private RMLTarget target;
    private Resource serialization;
    private Resource compression;
    private Resource encoding;

    public LogicalTarget(RMLTarget target, Resource serialization, Resource compression, Resource encoding) {
        this.target = target;
        this.serialization = serialization;
        this.compression = compression;
        this.encoding = encoding;
    }

    public LogicalTarget(RMLTarget target) {
        this.target = target;
    }

    public RMLTarget getTarget() {
        return target;
    }

    public Resource getSerialization() {
        return serialization;
    }

    public Resource getCompression() {
        return compression;
    }

    public Resource getEncoding() {
        return encoding;
    }
}
