package burp.model;

import burp.parse.loader.annotations.RdfProperty;
import burp.parse.loader.annotations.RdfShortcutProperty;
import burp.parse.loader.annotations.RdfType;
import burp.vocabularies.BURP;
import burp.vocabularies.RML;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RdfType(Rml.SubjectMap)
public class SubjectMap extends TermMap {
    @RdfProperty(Rml.class_)
    public List<Resource> classes = new ArrayList<>();

    @RdfProperty(Rml.graphMap)
    @RdfShortcutProperty(shortcutUri = Rml.graph, targetProperty = Rml.constant)
    public List<GraphMap> graphMaps = new ArrayList<>();

    @Override
    public @NonNull Iterable<PlanNode> children() {
        List<PlanNode> children = new ArrayList<>();
        super.children().forEach(children::add);
        children.addAll(graphMaps);
        return children;
    }

    @Override
    public @NonNull String getName() {
        return "subject map";
    }

    @Override
    public @NonNull Set<Resource> getAllowedTermTypes() {
        return Set.of(RML.IRI, RML.URI, RML.BLANKNODE, BURP.CollectionOrContainer);
    }
}