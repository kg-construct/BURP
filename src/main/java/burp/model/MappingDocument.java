package burp.model;

import burp.model.context.EvaluationContext;
import burp.model.key.AstKey;
import burp.model.rdf.*;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

import static burp.model.LogicalTarget.unionLogicalTargets;
import static burp.model.rdf.Datardf.iriTerm;
import static burp.model.rdf.Datardf.rdfUnderscore;

public class MappingDocument extends AbstractPlanNode {
    public final List<TriplesMap> triplesMaps;
    /**
     * Prefixes defined in the mapping file.
     * Do not use them for generation.
     * Use them to understand some kind of errors or do the error reporting.
     */
    public final Map<String, String> prefixMap;

    public MappingDocument(@NonNull List<TriplesMap> triplesMaps, @NonNull Map<String, String> prefixMap) {
        this.triplesMaps = triplesMaps;
        this.prefixMap = Collections.unmodifiableMap(prefixMap);
        for (TriplesMap tm : this.triplesMaps) {
            if (tm != null) {
                tm.setParent(this);
            }
        }
    }

    public MappingDocument(@NonNull List<TriplesMap> triplesMaps) {
        this(triplesMaps, Collections.emptyMap());
    }

    @Override
    public @Nullable Resource getIri() {
        return null;
    }

    @Override
    public Iterable<PlanNode> children() {
        return new ArrayList<>(triplesMaps);
    }

    @Override
    public Iterable<PlanNode> dependencies() {
        return new ArrayList<>(triplesMaps);
    }

    @Override
    public AstKey computeAstKey() {
        List<AstKey> childKeys = new ArrayList<>();
        for (PlanNode child : children()) {
            childKeys.add(child.getAstKey());
        }
        return new AstKey.DocumentKey(childKeys);
    }

    public List<RdfStatement> generate(EvaluationContext context) {
        List<RdfStatementLike> stmts = new ArrayList<>();
        for (TriplesMap tm : triplesMaps) {
            context.cancellationToken().throwIfCancelled();
            if (tm.logicalSource == null) {
                stmts.addAll(tm.generate(null, context));
            } else {
                var iter = tm.logicalSource.iterator();
                if (iter != null) {
                    for (var i : iter) {
                        stmts.addAll(tm.generate(i, context));
                    }
                }
            }
        }

        return postProcessContainers(stmts);
    }

    public List<RdfStatement> generate() {
        return generate(EvaluationContext.createDefault());
    }

    public static Term termItselfOrCollectionId(Term t) {
        if (t instanceof CollectionOrContainerTerm) {
            return ((CollectionOrContainerTerm) t).getId();
        }
        return t;
    }

    public List<RdfStatement> postProcessContainers(List<RdfStatementLike> stmts) {
        Map<IRITerm, Map<Term, CollectionOrContainerTerm>> containers = new HashMap<>();
        Map<IRITerm, Set<CollectionOrContainerTerm>> expandedContainer = new HashMap<>();
        List<RdfStatement> result = new ArrayList<>();

        for (RdfStatementLike stmt : stmts) {
            if (stmt instanceof RdfStatement rStmt) {
                extractAndMergeContainers(rStmt.getSubject(), rStmt.getGraph(), containers, expandedContainer, newTerm -> rStmt.setSubject((BlankNodeOrIRI) newTerm));
                extractAndMergeContainers(rStmt.getObject(), rStmt.getGraph(), containers, expandedContainer, newTerm -> rStmt.setObject(newTerm));
            } else if (stmt instanceof RdfStatementSubjectGraph rStmtGraph) {
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
                    BlankNodeTerm newId = new BlankNodeTerm("gathermap-" + (graph != null ? graph.toString() : "default") + "-" + index, c.targets(), c.lineage());
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

                switch (c) {
                    case RdfListTerm rdfListTerm -> {
                        Term currentListId = subject;
                        for (int i = 0; i < c.getElements().size(); i++) {
                            boolean isLast = i == c.getElements().size() - 1;
                            Term element = c.getElements().get(i);

                            OriginLineage elemLineage = element.lineage() != null ? (c.lineage() != null ? c.lineage().merge(element.lineage()) : element.lineage()) : c.lineage();
                            if (elemLineage != null && graph != null && graph.lineage() != null) {
                                elemLineage = elemLineage.merge(graph.lineage());
                            }
                            Set<LogicalTarget> elemTargets = unionLogicalTargets(c.targets(), element.targets(), graph != null ? graph.targets() : null);

                            containerStmts.add(new RdfStatement(
                                    (BlankNodeOrIRI) currentListId,
                                    iriTerm(RDF.first),
                                    termItselfOrCollectionId(element),
                                    graph,
                                    elemTargets,
                                    elemLineage
                            ));

                            Term restObj = isLast ? iriTerm(RDF.nil) : new BlankNodeTerm(subject.toString() + "_" + (i + 1), elemTargets, elemLineage);

                            containerStmts.add(new RdfStatement(
                                    (BlankNodeOrIRI) currentListId,
                                    iriTerm(RDF.rest),
                                    restObj,
                                    graph,
                                    elemTargets,
                                    elemLineage
                            ));

                            currentListId = restObj;
                        }
                    }
                    case RdfBagTerm rdfBagTerm ->
                            emitContainerStmts(c, subject, iriTerm(RDF.Bag), graph, containerStmts);
                    case RdfSeqTerm rdfSeqTerm ->
                            emitContainerStmts(c, subject, iriTerm(RDF.Seq), graph, containerStmts);
                    case RdfAltTerm rdfAltTerm ->
                            emitContainerStmts(c, subject, iriTerm(RDF.Alt), graph, containerStmts);
                    default -> {
                    }
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
                        it.targets(),
                        it.lineage()
                ))
                .toList();

        result.addAll(rdfStmtsWithoutCollections);
        result.addAll(containerStmts);

        return result;
    }

    private void emitContainerStmts(CollectionOrContainerTerm c, Term subject, IRITerm type, IRITerm graph, List<RdfStatement> containerStmts) {
        OriginLineage containerLineage = c.lineage();
        if (containerLineage != null && graph != null && graph.lineage() != null) {
            containerLineage = containerLineage.merge(graph.lineage());
        }
        Set<LogicalTarget> baseTargets = unionLogicalTargets(c.targets(), graph != null ? graph.targets() : null);
        containerStmts.add(new RdfStatement((BlankNodeOrIRI) subject, iriTerm(RDF.type), type, graph, baseTargets, containerLineage));
        for (int i = 0; i < c.getElements().size(); i++) {
            Term element = c.getElements().get(i);
            OriginLineage elemLineage = element.lineage() != null ? (c.lineage() != null ? c.lineage().merge(element.lineage()) : element.lineage()) : c.lineage();
            if (elemLineage != null && graph != null && graph.lineage() != null) {
                elemLineage = elemLineage.merge(graph.lineage());
            }
            Set<LogicalTarget> elemTargets = unionLogicalTargets(c.targets(), element.targets(), graph != null ? graph.targets() : null);
            containerStmts.add(new RdfStatement((BlankNodeOrIRI) subject, rdfUnderscore(i + 1), termItselfOrCollectionId(element), graph, elemTargets, elemLineage));
        }
    }

    private Term rewrite(Term t) {
        if (t instanceof RdfListTerm && ((RdfListTerm) t).getElements().isEmpty()) {
            return iriTerm(RDF.nil);
        }
        return termItselfOrCollectionId(t);
    }

    private void extractAndMergeContainers(Term t, IRITerm graph, Map<IRITerm, Map<Term, CollectionOrContainerTerm>> containers, Map<IRITerm, Set<CollectionOrContainerTerm>> expandedContainer, Consumer<Term> replaceTermBy) {
        if (t instanceof CollectionOrContainerTerm c) {
            Set<CollectionOrContainerTerm> set = expandedContainer.computeIfAbsent(graph, k -> new HashSet<>());
            if (set.add(c)) {
                Map<Term, CollectionOrContainerTerm> graphContainers = containers.computeIfAbsent(graph, k -> new HashMap<>());
                if (graphContainers.containsKey(c.getId())) {
                    CollectionOrContainerTerm existing = graphContainers.get(c.getId());
                    existing.getElements().addAll(c.getElements());
                    existing.mergeLineage(c.lineage());
                    existing.addTargets(c.targets());
                    replaceTermBy.accept(existing);
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
