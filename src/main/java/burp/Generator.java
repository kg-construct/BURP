package burp;

import burp.model.GraphMap;
import burp.model.Iteration;
import burp.model.ObjectMap;
import burp.model.PredicateMap;
import burp.model.PredicateObjectMap;
import burp.model.ReferencingObjectMap;
import burp.model.SubjectMap;
import burp.model.TriplesMap;
import burp.model.gathermaputil.SubGraph;
import burp.vocabularies.RML;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.vocabulary.RDF;

/**
 * Generates quadruples by executing supplied RML triples maps.
 */
public class Generator {

    private final List<RDFNode> def = List.of(RML.defaultGraph);

    /**
     * Executes the supplied RML triples maps and streams the generated RDF quads to the provided consumer.
     * <p>
     * No dataset is materialized in memory. Each generated quad is emitted immediately by invoking {@link Consumer#accept(Object)} on the supplied consumer.
     * The order of emitted quads follows the order in which triples maps, logical source iterations, subject maps, and predicate-object maps are processed by
     * the generator. A {@code Consumer} is intended for side-effecting operations such as writing, indexing, or collecting generated data.
     *
     * @param triplesmaps the triples maps to execute
     * @param baseIRI base IRI used to resolve relative IRIs produced by the mapping
     * @param consumer callback invoked once for every generated quad
     */
    public void generate(List<TriplesMap> triplesmaps,
            String baseIRI,
            Consumer<Quad> consumer) {

        for (TriplesMap tm : triplesmaps) {

            SubjectMap sm = tm.subjectMap;
            List<GraphMap> sgm = sm.graphMaps;

            Iterator<Iteration> iter = tm.logicalSource.iterator();
            while (iter.hasNext()) {

                Iteration i = iter.next();

                // subject graphs
                List<RDFNode> sgs = sgm.isEmpty()
                        ? def
                        : new ArrayList<>();

                for (GraphMap gm : sgm) {
                    sgs.addAll(gm.generateTerms(i, baseIRI));
                }

                // subjects
                List<RDFNode> subjects = new ArrayList<>();

                if (!sm.isGatherMap()) {
                    subjects.addAll(sm.generateTerms(i, baseIRI));
                } else {
                    for (SubGraph subgraph : sm.generateGatherMapGraphs(i, baseIRI)) {
                        subjects.add(subgraph.node);
                        emitSubGraph(subgraph, sgs, consumer);
                    }
                }

                // rdf:type from subject map
                emitSubjectTypes(sm.classes, subjects, sgs, consumer);

                // predicate-object maps
                for (PredicateObjectMap pom : tm.predicateObjectMaps) {

                    List<RDFNode> pogs = new ArrayList<>();
                    for (GraphMap gm : pom.graphMaps) {
                        pogs.addAll(gm.generateTerms(i, baseIRI));
                    }

                    List<RDFNode> graphs = def;

                    if (!sgm.isEmpty() || !pogs.isEmpty()) {
                        pogs.addAll(!sgm.isEmpty() ? sgs : new ArrayList<>());
                        graphs = pogs;
                    }

                    List<RDFNode> predicates = new ArrayList<>();
                    for (PredicateMap pm : pom.predicateMaps) {
                        predicates.addAll(pm.generateTerms(i, baseIRI));
                    }

                    List<RDFNode> objects = new ArrayList<>();

                    for (ObjectMap om : pom.objectMaps) {
                        if (!om.isGatherMap()) {
                            objects.addAll(om.generateTerms(i, baseIRI));
                        } else {
                            for (SubGraph subgraph : om.generateGatherMapGraphs(i, baseIRI)) {
                                objects.add(subgraph.node);
                                emitSubGraph(subgraph, graphs, consumer);
                            }
                        }
                    }

                    for (ReferencingObjectMap rom : pom.refObjectMaps) {
                        objects.addAll(rom.generateTerms(i, baseIRI));
                    }

                    emitTriples(subjects, predicates, objects, graphs, consumer);
                }
            }
        }
    }

    /* ---------------- Quad emission helpers ---------------- */
    private void emitTriples(List<RDFNode> subjects,
            List<RDFNode> predicates,
            List<RDFNode> objects,
            List<RDFNode> graphs,
            Consumer<Quad> consumer) {

        for (RDFNode s : subjects) {
            Node sn = s.asNode();

            for (RDFNode p : predicates) {
                Node pn = p.asNode();

                for (RDFNode o : objects) {
                    Node on = o.asNode();

                    for (RDFNode g : graphs) {
                        Node gn = toGraphNode(g);
                        consumer.accept(Quad.create(gn, sn, pn, on));
                    }
                }
            }
        }
    }

    private void emitSubjectTypes(List<Resource> classes,
            List<RDFNode> subjects,
            List<RDFNode> graphs,
            Consumer<Quad> consumer) {

        Node rdfType = RDF.type.asNode();

        for (RDFNode s : subjects) {
            Node sn = s.asNode();

            for (Resource c : classes) {
                Node cn = c.asNode();

                for (RDFNode g : graphs) {
                    Node gn = toGraphNode(g);
                    consumer.accept(Quad.create(gn, sn, rdfType, cn));
                }
            }
        }
    }

    /* ---------------- SubGraph handling ---------------- */
    private void emitSubGraph(SubGraph subgraph,
            List<RDFNode> graphs,
            Consumer<Quad> consumer) {

        Node gn = null;

        for (RDFNode g : graphs) {
            gn = toGraphNode(g);

            Node finalGn = gn;

            // emit all triples from subgraph.model into quads
            subgraph.model.listStatements().forEachRemaining(st -> {
                consumer.accept(Quad.create(
                        finalGn,
                        st.getSubject().asNode(),
                        st.getPredicate().asNode(),
                        st.getObject().asNode()
                ));
            });
        }
    }

    /* ---------------- graph conversion ---------------- */
    private Node toGraphNode(RDFNode g) {
        if (g.equals(RML.defaultGraph)) {
            return Quad.defaultGraphIRI;
        }
        return g.asNode();
    }

}
