package burp.model;

import burp.parse.loader.annotations.RdfId;
import burp.parse.loader.annotations.RdfProperty;
import burp.parse.loader.annotations.RdfType;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

@RdfType(Rml.LogicalTarget)
public class LogicalTarget {
    public static final LogicalTarget DEFAULT = new LogicalTarget(null);

    @RdfId
    public Resource subject;

    @RdfProperty(Rml.target)
    @Nullable
    public RMLTarget target;

    @RdfProperty(Rml.serialization)
    @Nullable
    public Resource serialization;

    @RdfProperty(Rml.compression)
    @Nullable
    public Resource compression;

    @RdfProperty(Rml.encoding)
    @Nullable
    public Resource encoding;

    public LogicalTarget() {
    }

    public LogicalTarget(@Nullable RMLTarget target) {
        this.target = target;
    }

    public boolean isDefault() {
        return target == null;
    }

    public String getId() {
        if (isDefault()) return "default";
        if (target instanceof FilePathTarget fpt) return fpt.path();
        return toString();
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LogicalTarget that)) return false;
        return java.util.Objects.equals(target, that.target) &&
                java.util.Objects.equals(serialization, that.serialization) &&
                java.util.Objects.equals(compression, that.compression) &&
                java.util.Objects.equals(encoding, that.encoding);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(target, serialization, compression, encoding);
    }
}
