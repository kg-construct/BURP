package burp.model;

import burp.vocabularies.BURP;
import burp.vocabularies.RML;
import org.apache.jena.rdf.model.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SubjectMap extends TermMap {
    public List<Resource> classes = new ArrayList<>();
    public List<GraphMap> graphMaps = new ArrayList<>();

    public SubjectMap() {
        this.termType = RML.IRI;
    }

    @Override
    public Iterable<PlanNode> children() {
        List<PlanNode> children = new ArrayList<>();
        super.children().forEach(children::add);
        graphMaps.forEach(children::add);
        return children;
    }

    @Override
    public String getName() {
        return "subject map";
    }

    @Override
    public Set<Resource> getAllowedTermTypes() {
        return Set.of(RML.IRI, RML.URI, RML.BLANKNODE, BURP.CollectionOrContainer);
    }
}