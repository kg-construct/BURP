package burp.parse.turtleprov;

import burp.vocabularies.BURP;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class RDF12Converter {

    private static int nextBlankId = 0;

    private static Resource nextReifierId(Model model) {
        return model.createResource(new AnonId("reifier_" + (nextBlankId++)));
    }

    public static Model toModel(ProvStore store) {
        return toModel(store, true);
    }

    public static Model toModel(ProvStore store, boolean withAnnotations) {
        Model model = ModelFactory.createDefaultModel();

        for (ProvTriple provQuad : store.getTriples()) {
            Statement quad = provQuad.statement();
            model.add(quad);

            if (withAnnotations) {
                String docUri = provQuad.documentUri() != null ? provQuad.documentUri() : store.getDocumentUri();
                if (provQuad.subjectInfo() != null) {
                    addNodeAnnotations(model, quad, BURP.SUBJECT, provQuad.subjectInfo(), docUri);
                }
                if (provQuad.predicateInfo() != null) {
                    addNodeAnnotations(model, quad, BURP.PREDICATE, provQuad.predicateInfo(), docUri);
                }
                if (provQuad.objectInfo() != null) {
                    addNodeAnnotations(model, quad, BURP.OBJECT, provQuad.objectInfo(), docUri);
                }
            }
        }

        return model;
    }

    private static void addNodeAnnotations(Model model, Statement mainQuad, Resource kindProperty, NodeInfo nodeInfo, @org.jspecify.annotations.Nullable String documentUri) {
        Resource reifier = nextReifierId(model);
        model.add(reifier, RDF.reifies, model.createStatementTerm(mainQuad));
        model.add(reifier, RDF.type, kindProperty);

        if (documentUri != null) {
            model.add(reifier, BURP.DocumentUri, model.createResource(documentUri));
        }

        if (nodeInfo.kind() != null) {
            model.add(reifier, BURP.TOKEN, model.createResource(nodeInfo.kind().getUri()));
        }

        addPoint(model, reifier, BURP.START_LINE, BURP.START_COLUMN, nodeInfo.start());
        addPoint(model, reifier, BURP.END_LINE, BURP.END_COLUMN, nodeInfo.end());

        addPoint(model, reifier, BURP.STRING_START_LINE, BURP.STRING_START_COLUMN, nodeInfo.rdfLiteralStringStart());
        addPoint(model, reifier, BURP.STRING_END_LINE, BURP.STRING_END_COLUMN, nodeInfo.rdfLiteralStringEnd());

        if (nodeInfo.blankNodeId() != null) {
            model.add(reifier, BURP.BLANK_NODE_ID, nodeInfo.blankNodeId());
        }

        if (nodeInfo.offsetMapper() != null) {
            String encoded = nodeInfo.offsetMapper().encode();
            if (encoded != null) {
                model.add(reifier, BURP.OFFSET_MAPPER, model.createLiteral(encoded));
            }
        }
    }

    private static void addPoint(Model model, Resource reifier, Property lineProp, Property colProp, Point pt) {
        if (pt != null) {
            model.add(reifier, lineProp, model.createTypedLiteral(pt.line()));
            model.add(reifier, colProp, model.createTypedLiteral(pt.column()));
        }
    }

    public record TripleInfo(
            NodeInfo subjectInfo,
            NodeInfo predicateInfo,
            NodeInfo objectInfo,
            @Nullable String documentUri
    ) {
        public TripleInfo(NodeInfo subjectInfo, NodeInfo predicateInfo, NodeInfo objectInfo) {
            this(subjectInfo, predicateInfo, objectInfo, null);
        }

        public List<NodeInfo> toList() {
            return Arrays.asList(subjectInfo, predicateInfo, objectInfo);
        }

        public @Nullable Path sourcePath() {
            if (documentUri == null) return null;
            try {
                URI uri = URI.create(documentUri);
                if ("file".equalsIgnoreCase(uri.getScheme())) {
                    return Path.of(uri);
                }
            } catch (Exception ignored) {}
            return null;
        }
    }

    public static TripleInfo fromAnnotations(Statement targetTriple, Model model) {
        RDFNode stmtTerm = model.createStatementTerm(targetTriple);
        ResIterator reifiers = model.listSubjectsWithProperty(RDF.reifies, stmtTerm);

        NodeInfo subjInfo = null;
        NodeInfo predInfo = null;
        NodeInfo objInfo = null;
        String documentUri = null;

        while (reifiers.hasNext()) {
            Resource reifier = reifiers.nextResource();
            Resource typeRes = reifier.getPropertyResourceValue(RDF.type);
            NodeInfo info = extractNodeInfo(reifier);
            if (documentUri == null && reifier.hasProperty(BURP.DocumentUri)) {
                RDFNode docNode = reifier.getProperty(BURP.DocumentUri).getObject();
                if (docNode.isURIResource()) {
                    documentUri = docNode.asResource().getURI();
                } else if (docNode.isLiteral()) {
                    documentUri = docNode.asLiteral().getString();
                }
            }

            if (typeRes != null) {
                String uri = typeRes.getURI();
                if (BURP.SUBJECT.getURI().equals(uri)) {
                    subjInfo = info;
                } else if (BURP.PREDICATE.getURI().equals(uri)) {
                    predInfo = info;
                } else if (BURP.OBJECT.getURI().equals(uri)) {
                    objInfo = info;
                }
            }
        }
        return new TripleInfo(subjInfo, predInfo, objInfo, documentUri);
    }

    private static NodeInfo extractNodeInfo(Resource reifier) {
        String tokenUri = uriProp(reifier, BURP.TOKEN);

        TurtleNodeKind kind = null;
        for (TurtleNodeKind k : TurtleNodeKind.values()) {
            if (k.getUri().equals(tokenUri)) {
                kind = k;
                break;
            }
        }

        String encodedMapper = stringProp(reifier, BURP.OFFSET_MAPPER);
        OffsetMapper offsetMapper = encodedMapper != null ? OffsetMapper.decode(encodedMapper) : null;

        return new NodeInfo(
            kind,
            point(reifier, BURP.START_LINE, BURP.START_COLUMN),
            point(reifier, BURP.END_LINE, BURP.END_COLUMN),
            point(reifier, BURP.STRING_START_LINE, BURP.STRING_START_COLUMN),
            point(reifier, BURP.STRING_END_LINE, BURP.STRING_END_COLUMN),
            stringProp(reifier, BURP.BLANK_NODE_ID),
            offsetMapper
        );
    }

    private static Integer intProp(Resource reifier, Property p) {
        Statement stmt = reifier.getProperty(p);
        if (stmt != null && stmt.getObject().isLiteral()) {
            return stmt.getObject().asLiteral().getInt();
        }
        return null;
    }

    private static String stringProp(Resource reifier, Property p) {
        Statement stmt = reifier.getProperty(p);
        if (stmt != null && stmt.getObject().isLiteral()) {
            return stmt.getObject().asLiteral().getString();
        }
        return null;
    }

    private static String uriProp(Resource reifier, Property p) {
        Statement stmt = reifier.getProperty(p);
        if (stmt != null && stmt.getObject().isResource()) {
            return stmt.getObject().asResource().getURI();
        }
        return null;
    }

    private static Point point(Resource reifier, Property lineProp, Property colProp) {
        Integer lineVal = intProp(reifier, lineProp);
        Integer colVal = intProp(reifier, colProp);
        if (lineVal != null && colVal != null) {
            return new Point(lineVal, colVal);
        }
        return null;
    }
}
