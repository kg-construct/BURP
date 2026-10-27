package burp.dev.rml.service.trace;

import burp.dev.rml.service.selector.StructureSelector;
import burp.model.OriginLineage;
import burp.model.RdfStatement;
import burp.model.rdf.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Parameters for requesting a backward provenance slicing trace of a specific result quad.
 */
@NullMarked
public record TraceResultParams(
        StructureSelector structure,
        String subject,
        String predicate,
        String object,
        @Nullable String graph,
        @Nullable OriginLineage lineage
) {
    public TraceResultParams(StructureSelector structure, String subject, String predicate, String object, @Nullable String graph) {
        this(structure, subject, predicate, object, graph, null);
    }

    public TraceResultParams(StructureSelector structure, String subject, String predicate, String object) {
        this(structure, subject, predicate, object, null, null);
    }

    public BlankNodeOrIRI getSubjectTerm() {
        return subject.startsWith("_:") ? new BlankNodeTerm(subject) : new IRITerm(subject);
    }

    public IRITerm getPredicateTerm() {
        return new IRITerm(predicate);
    }

    public Term getObjectTerm() {
        return Datardf.toTerm(object);
    }

    public @Nullable IRITerm getGraphTerm() {
        return (graph != null && !graph.isBlank()) ? new IRITerm(graph) : null;
    }

    public RdfStatement toRdfStatement() {
        return new RdfStatement(getSubjectTerm(), getPredicateTerm(), getObjectTerm(), getGraphTerm(), java.util.Set.of(), lineage);
    }
}
