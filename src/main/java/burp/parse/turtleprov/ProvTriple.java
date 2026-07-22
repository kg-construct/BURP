package burp.parse.turtleprov;

import org.apache.jena.rdf.model.Statement;
import java.util.Objects;

public final class ProvTriple {
    public final Statement statement;
    public final NodeInfo subjectInfo;
    public final NodeInfo predicateInfo;
    public final NodeInfo objectInfo;

    public ProvTriple(Statement statement, NodeInfo subjectInfo, NodeInfo predicateInfo, NodeInfo objectInfo) {
        this.statement = statement;
        this.subjectInfo = subjectInfo;
        this.predicateInfo = predicateInfo;
        this.objectInfo = objectInfo;
    }

    public ProvTriple(Statement statement) {
        this(statement, null, null, null);
    }

    public Statement statement() { return statement; }
    public NodeInfo subjectInfo() { return subjectInfo; }
    public NodeInfo predicateInfo() { return predicateInfo; }
    public NodeInfo objectInfo() { return objectInfo; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProvTriple that)) return false;
        return Objects.equals(statement, that.statement) &&
                Objects.equals(subjectInfo, that.subjectInfo) &&
                Objects.equals(predicateInfo, that.predicateInfo) &&
                Objects.equals(objectInfo, that.objectInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(statement, subjectInfo, predicateInfo, objectInfo);
    }

    @Override
    public String toString() {
        return "ProvTriple[statement=" + statement + ", subjectInfo=" + subjectInfo +
                ", predicateInfo=" + predicateInfo + ", objectInfo=" + objectInfo + "]";
    }
}
