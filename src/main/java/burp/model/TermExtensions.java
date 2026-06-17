package burp.model;

import burp.model.rdf.CollectionOrContainerTerm;
import burp.model.rdf.Term;

public class TermExtensions {
    public static Term itselfOrId(Term t) {
        if (t instanceof CollectionOrContainerTerm) {
            return ((CollectionOrContainerTerm) t).getId();
        }
        return t;
    }
}
