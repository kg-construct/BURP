package burp.model;

import burp.model.rdf.IRITerm;
import burp.model.rdf.Term;

import java.util.Set;

public record RdfPredicateObject(IRITerm predicate, Term object, IRITerm graph, Set<LogicalTarget> targets) {
    public RdfPredicateObject(IRITerm predicate, Term object) {
        this(predicate, object, null, Set.of());
    }
    
    public RdfPredicateObject(IRITerm predicate, Term object, IRITerm graph) {
        this(predicate, object, graph, Set.of());
    }
}
