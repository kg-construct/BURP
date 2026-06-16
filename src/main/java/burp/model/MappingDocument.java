package burp.model;

import burp.model.rdf.*;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.RER;
import org.apache.jena.vocabulary.RDF;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MappingDocument implements PlanNode {
    public final List<TriplesMap> triplesMaps;
    private PlanNode parent = null;

    public MappingDocument(List<TriplesMap> triplesMaps) {
        this.triplesMaps = triplesMaps;
    }

    @Override
    public PlanNode getParent() {
        return parent;
    }

    @Override
    public void setParent(PlanNode parent) {
        this.parent = parent;
    }

    @Override
    public Iterable<PlanNode> children() {
        return new ArrayList<>(triplesMaps);
    }

    @Override
    public Iterable<PlanNode> dependencies() {
        return new ArrayList<>(triplesMaps);
    }

    public List<RdfStatement> generate() {
        List<RdfStatementLike> stmts = new ArrayList<>();
        for (TriplesMap tm : triplesMaps) {
            var iter = tm.logicalSource.iterator();
            if (iter == null) {
                throw new BurpException(new RmlError(
                        "Constant Triples Map without logical source",
                        new Origin(this, Collections.emptyList()),
                        RER.UnsupportedMapping
                ));
            }
            for (var i : iter) {
                stmts.addAll(tm.generate(i));
            }
        }

        return postProcessContainers(stmts);
    }

    private List<RdfStatement> postProcessContainers(List<RdfStatementLike> stmts) {
        Map<IRITerm, Map<Term, CollectionOrContainerTerm>> containers = new HashMap<>();
        Map<IRITerm, Set<CollectionOrContainerTerm>> expandedContainer = new HashMap<>();
        List<RdfStatement> result = new ArrayList<>();

        for (RdfStatementLike stmt : stmts) {
            if (stmt instanceof RdfStatement) {
                RdfStatement rStmt = (RdfStatement) stmt;
                extractAndMergeContainers(rStmt.getSubject(), rStmt.getGraph(), containers, expandedContainer, newTerm -> rStmt.setSubject((BlankNodeOrIRI) newTerm));
                extractAndMergeContainers(rStmt.getObject(), rStmt.getGraph(), containers, expandedContainer, newTerm -> rStmt.setObject(newTerm));
            } else if (stmt instanceof RdfStatementSubjectGraph) {
                RdfStatementSubjectGraph rStmtGraph = (RdfStatementSubjectGraph) stmt;
                extractAndMergeContainers(rStmtGraph.getSubject(), rStmtGraph.getGraph(), containers, expandedContainer, newTerm -> rStmtGraph.setSubject((BlankNodeOrIRI) newTerm));
            }
        }

        for (Map.Entry<IRITerm, Map<Term, CollectionOrContainerTerm>> entry : containers.entrySet()) {
            IRITerm graph = entry.getKey();
            Map<Term, CollectionOrContainerTerm> graphContainers = entry.getValue();

            List<CollectionOrContainerTerm> values = new ArrayList<>(graphContainers.values());
            for (int index = 0; index < values.size(); index++) {
                CollectionOrContainerTerm c = values.get(index);
                if (c.getId() instanceof BlankNodeTerm) {
                    BlankNodeTerm newId = new BlankNodeTerm("gathermap-" + (graph != null ? graph.toString() : "default") + "-" + index);
                    graphContainers.get(c.getId()).setId(newId);
                }
            }
        }

        List<RdfStatement> containerStmts = new ArrayList<>();

        for (Map.Entry<IRITerm, Map<Term, CollectionOrContainerTerm>> entry : containers.entrySet()) {
            IRITerm graph = entry.getKey();
            Map<Term, CollectionOrContainerTerm> graphContainers = entry.getValue();

            for (CollectionOrContainerTerm c : graphContainers.values()) {
                Term subject = c.getId();

                if (c instanceof RdfListTerm) {
                    Term currentListId = subject;
                    for (int i = 0; i < c.getElements().size(); i++) {
                        boolean isLast = i == c.getElements().size() - 1;
                        Term element = c.getElements().get(i);

                        containerStmts.add(new RdfStatement(
                                (BlankNodeOrIRI) currentListId,
                                new IRITerm(RDF.first.getURI()),
                                TermExtensions.itselfOrId(element),
                                graph,
                                null
                        ));

                        Term restObj = isLast ? new IRITerm(RDF.nil.getURI()) : new BlankNodeTerm(subject.toString() + "_" + (i + 1));

                        containerStmts.add(new RdfStatement(
                                (BlankNodeOrIRI) currentListId,
                                new IRITerm(RDF.rest.getURI()),
                                restObj,
                                graph,
                                null
                        ));

                        currentListId = restObj;
                    }
                } else if (c instanceof RdfBagTerm) {
                    emitContainerStmts(c, subject, new IRITerm(RDF.Bag.getURI()), graph, containerStmts);
                } else if (c instanceof RdfSeqTerm) {
                    emitContainerStmts(c, subject, new IRITerm(RDF.Seq.getURI()), graph, containerStmts);
                } else if (c instanceof RdfAltTerm) {
                    emitContainerStmts(c, subject, new IRITerm(RDF.Alt.getURI()), graph, containerStmts);
                }
            }
        }

        List<RdfStatement> rdfStmtsWithoutCollections = stmts.stream()
                .filter(s -> s instanceof RdfStatement)
                .map(s -> (RdfStatement) s)
                .map(it -> new RdfStatement(
                        (BlankNodeOrIRI) rewrite(it.getSubject()),
                        it.getPredicate(),
                        rewrite(it.getObject()),
                        it.getGraph(),
                        it.targets()
                ))
                .collect(Collectors.toList());

        result.addAll(rdfStmtsWithoutCollections);
        result.addAll(containerStmts);

        return result;
    }

    private void emitContainerStmts(CollectionOrContainerTerm c, Term subject, IRITerm type, IRITerm graph, List<RdfStatement> containerStmts) {
        containerStmts.add(new RdfStatement((BlankNodeOrIRI) subject, new IRITerm(RDF.type.getURI()), type, graph, null));
        for (int i = 0; i < c.getElements().size(); i++) {
            Term element = c.getElements().get(i);
            containerStmts.add(new RdfStatement((BlankNodeOrIRI) subject, new IRITerm("http://www.w3.org/1999/02/22-rdf-syntax-ns#_" + (i + 1)), TermExtensions.itselfOrId(element), graph, null));
        }
    }

    private Term rewrite(Term t) {
        if (t instanceof RdfListTerm && ((RdfListTerm) t).getElements().isEmpty()) {
            return new IRITerm(RDF.nil.getURI());
        }
        if (t instanceof CollectionOrContainerTerm) {
            return ((CollectionOrContainerTerm) t).getId();
        }
        return t;
    }

    private void extractAndMergeContainers(Term t, IRITerm graph, Map<IRITerm, Map<Term, CollectionOrContainerTerm>> containers, Map<IRITerm, Set<CollectionOrContainerTerm>> expandedContainer, Consumer<Term> replaceTermBy) {
        if (t instanceof CollectionOrContainerTerm) {
            CollectionOrContainerTerm c = (CollectionOrContainerTerm) t;
            Set<CollectionOrContainerTerm> set = expandedContainer.computeIfAbsent(graph, k -> new HashSet<>());
            if (set.add(c)) {
                Map<Term, CollectionOrContainerTerm> graphContainers = containers.computeIfAbsent(graph, k -> new HashMap<>());
                if (graphContainers.containsKey(c.getId())) {
                    graphContainers.get(c.getId()).getElements().addAll(c.getElements());
                    replaceTermBy.accept(graphContainers.get(c.getId()));
                } else {
                    graphContainers.put(c.getId(), c);
                }
                for (Term element : c.getElements()) {
                    extractAndMergeContainers(element, graph, containers, expandedContainer, replaceTermBy);
                }
            }
        }
    }
}
