package burp.model;

import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.NonNull;

import java.util.HashSet;
import java.util.Set;

public class LogicalTarget {
    private final RMLTarget target;
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

    @SafeVarargs
    @NonNull
    public static Set<LogicalTarget> unionLogicalTargets(Set<LogicalTarget>... targetSets) {
        Set<LogicalTarget> union = new HashSet<>();
        for (Set<LogicalTarget> set : targetSets) {
            if (set != null && !set.isEmpty()) {
                union.addAll(set);
            }
        }
        return union;
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
