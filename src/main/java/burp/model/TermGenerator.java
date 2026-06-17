package burp.model;

import burp.model.rdf.Term;

import java.util.List;

public interface TermGenerator extends PlanNode {
    List<Term> generateTerms(Iteration i);
}
