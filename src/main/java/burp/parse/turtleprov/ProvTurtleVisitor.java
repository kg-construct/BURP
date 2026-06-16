package burp.parse.turtleprov;

import burp.parse.turtleprov.generated.TurtleBaseVisitor;
import burp.parse.turtleprov.generated.TurtleParser;
import burp.reporting.PointRange;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProvTurtleVisitor extends TurtleBaseVisitor<Object> {
    public static List<PointRange> retrieveTurtleLocation(Object o) { return Collections.emptyList(); }
    private final ProvStore store = new ProvStore();

    public ProvStore getStore() {
        return store;
    }
    private final Model model = ModelFactory.createDefaultModel();
    private final Map<String, Resource> blankNodeMap = new HashMap<>();

    private Resource createBlankNode() {
        return createBlankNode(null);
    }

    private Resource createBlankNode(String id) {
        return model.createResource(id != null ? new AnonId(id) : new AnonId());
    }

    private static final Pattern prefixedNamePattern = Pattern.compile("^(.*?):(.*)$");
    private static final Pattern blankNodeLabelPattern = Pattern.compile("^_:(.+)$");

    private Point getStartPoint(Token token) {
        if (token == null) return null;
        return new Point(token.getLine() - 1, token.getCharPositionInLine());
    }

    private Point getEndPoint(Token token) {
        if (token == null) return null;
        String text = token.getText();
        if (text == null) return getStartPoint(token);
        int newLines = 0;
        int lastLineIdx = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                newLines++;
                lastLineIdx = i;
            }
        }
        int endLine = token.getLine() - 1 + newLines;
        int endCol = newLines == 0 ? token.getCharPositionInLine() + text.length() : text.length() - lastLineIdx - 1;
        return new Point(endLine, endCol);
    }

    @Override
    protected Object defaultResult() {
        return null;
    }

    @Override
    public ProvStore visitTurtleDoc(TurtleParser.TurtleDocContext ctx) {
        for (TurtleParser.StatementContext stmt : ctx.statement()) {
            visit(stmt);
        }
        return store;
    }

    @Override
    public Object visitStatement(TurtleParser.StatementContext ctx) {
        TurtleParser.DirectiveContext directive = ctx.directive();
        if (directive != null) {
            return visitDirective(directive);
        }
        TurtleParser.TriplesContext triples = ctx.triples();
        if (triples != null) {
            return visit(triples);
        }
        return null;
    }

    public Object makeRegisterPrefix(TerminalNode prefix, TerminalNode iriref, boolean isSparql) {
        String prefixText = prefix.getText();
        String prefixNs = prefixText.substring(0, prefixText.length() - 1);
        String iriText = iriref.getText();
        String iri = iriText.substring(1, iriText.length() - 1);
        store.getPrefixes().put(prefixNs, iri);
        return null;
    }

    @Override
    public Object visitPrefixID(TurtleParser.PrefixIDContext ctx) {
        return makeRegisterPrefix(ctx.PNAME_NS(), ctx.IRIREF(), false);
    }

    @Override
    public Object visitSparqlPrefix(TurtleParser.SparqlPrefixContext ctx) {
        return makeRegisterPrefix(ctx.PNAME_NS(), ctx.IRIREF(), false);
    }

    @Override
    public Object visitBase(TurtleParser.BaseContext ctx) {
        return null;
    }

    @Override
    public Object visitSparqlBase(TurtleParser.SparqlBaseContext ctx) {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object visitTriples(TurtleParser.TriplesContext ctx) {
        TurtleParser.SubjectContext subjectCtx = ctx.subject();
        TurtleParser.PredicateObjectListContext predicateObjectListCtx = ctx.predicateObjectList();
        TurtleParser.BlankNodePropertyListContext blankNodePropertyListCtx = ctx.blankNodePropertyList();

        if (subjectCtx != null && predicateObjectListCtx != null) {
            Pair<Resource, NodeInfo> subject = (Pair<Resource, NodeInfo>) visitSubject(subjectCtx);
            visitPredicateObjectList(predicateObjectListCtx, subject);
        } else if (blankNodePropertyListCtx != null) {
            Pair<Resource, NodeInfo> blankNode = (Pair<Resource, NodeInfo>) visitBlankNodePropertyList(blankNodePropertyListCtx);
            if (predicateObjectListCtx != null) {
                visitPredicateObjectList(predicateObjectListCtx, blankNode);
            }
        }
        return null;
    }

    @Override
    public Object visitSubject(TurtleParser.SubjectContext ctx) {
        if (ctx.iri() != null) return visitIri(ctx.iri());
        if (ctx.BlankNode() != null) return visitBlankNodeTerminal(ctx.BlankNode());
        if (ctx.collection() != null) return visitCollection(ctx.collection());
        throw new IllegalArgumentException("Unknown subject type");
    }

    @Override
    public Object visitIri(TurtleParser.IriContext ctx) {
        TerminalNode irirefCtx = ctx.IRIREF();
        TerminalNode prefixedNameCtx = ctx.PrefixedName();

        if (irirefCtx != null) {
            Token token = irirefCtx.getSymbol();
            String iriText = token.getText();
            String iri = iriText.substring(1, iriText.length() - 1);
            Resource resource = ResourceFactory.createResource(iri);
            NodeInfo nodeInfo = new NodeInfo(
                TurtleNodeKind.IRIREF,
                getStartPoint(token),
                getEndPoint(token),
                null
            );
            return new Pair<>(resource, nodeInfo);
        } else if (prefixedNameCtx != null) {
            Token token = prefixedNameCtx.getSymbol();
            String prefixedName = token.getText() != null ? token.getText() : "";
            Matcher match = prefixedNamePattern.matcher(prefixedName);

            if (match.matches()) {
                String prefix = match.group(1) != null ? match.group(1) : "";
                String localName = match.group(2);
                String namespace = store.getPrefixes().get(prefix);
                if (namespace == null) throw new IllegalArgumentException("Unknown prefix: " + prefix);
                Resource resource = ResourceFactory.createResource(namespace + localName);
                NodeInfo nodeInfo = new NodeInfo(TurtleNodeKind.PREFIXED_NAME, getStartPoint(token), getEndPoint(token), null);
                return new Pair<>(resource, nodeInfo);
            } else {
                throw new IllegalArgumentException("Invalid prefixed name: " + prefixedName);
            }
        } else {
            throw new IllegalArgumentException("Unknown IRI type");
        }
    }

    private Pair<Resource, NodeInfo> visitBlankNodeTerminal(TerminalNode terminalNode) {
        Token token = terminalNode.getSymbol();
        String text = token.getText() != null ? token.getText() : "";

        if (text.startsWith("_:")) {
            Matcher matcher = blankNodeLabelPattern.matcher(text);
            if (matcher.matches()) {
                String label = matcher.group(1);
                Resource resource = blankNodeMap.computeIfAbsent(label, this::createBlankNode);
                NodeInfo nodeInfo = new NodeInfo(
                    TurtleNodeKind.BLANK_NODE_LABEL,
                    getStartPoint(token), getEndPoint(token), label
                );
                return new Pair<>(resource, nodeInfo);
            } else {
                throw new IllegalArgumentException("Invalid blank node label: " + text);
            }
        } else if (text.startsWith("[") && text.endsWith("]")) {
            Resource resource = createBlankNode();
            NodeInfo nodeInfo = new NodeInfo(
                TurtleNodeKind.ANONYMOUS_BLANK_NODE,
                getStartPoint(token), getEndPoint(token), null
            );
            return new Pair<>(resource, nodeInfo);
        } else {
            throw new IllegalArgumentException("Unknown blank node format: " + text);
        }
    }

    public Resource assembleList(List<RDFNode> list) {
        if (list.isEmpty()) {
            return (Resource) RDF.nil;
        } else {
            List<Resource> blankNodes = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                blankNodes.add(createBlankNode());
            }
            for (int i = 0; i < list.size(); i++) {
                Resource node = blankNodes.get(i);
                RDFNode value = list.get(i);
                store.getTriples().add(new ProvTriple(model.createStatement(node, RDF.first, value)));
                store.getTriples().add(new ProvTriple(model.createStatement(node, RDF.type, RDF.List)));
            }
            for (int i = 0; i < blankNodes.size() - 1; i++) {
                Resource current = blankNodes.get(i);
                Resource next = blankNodes.get(i + 1);
                store.getTriples().add(new ProvTriple(model.createStatement(current, RDF.rest, next)));
            }
            store.getTriples().add(new ProvTriple(model.createStatement(blankNodes.get(blankNodes.size() - 1), RDF.rest, (Resource) RDF.nil)));
            return blankNodes.get(0);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object visitCollection(TurtleParser.CollectionContext ctx) {
        List<RDFNode> items = new ArrayList<>();
        for (TurtleParser.Object_Context objCtx : ctx.object_()) {
            Pair<RDFNode, NodeInfo> p = (Pair<RDFNode, NodeInfo>) visitObject_(objCtx);
            items.add(p.first());
        }
        Resource collectionStart = assembleList(items);
        NodeInfo nodeInfo = new NodeInfo(
            TurtleNodeKind.COLLECTION,
            getStartPoint(ctx.start), getEndPoint(ctx.stop), null
        );

        return new Pair<>(collectionStart, nodeInfo);
    }

    @Override
    public Object visitBlankNodePropertyList(TurtleParser.BlankNodePropertyListContext ctx) {
        Resource resource = createBlankNode();
        NodeInfo nodeInfo = new NodeInfo(
            TurtleNodeKind.BLANK_NODE_PROPERTY_LIST,
            getStartPoint(ctx.start), getEndPoint(ctx.stop), null
        );

        Pair<Resource, NodeInfo> subject = new Pair<>(resource, nodeInfo);
        if (ctx.predicateObjectList() != null) {
            visitPredicateObjectList(ctx.predicateObjectList(), subject);
        }

        return subject;
    }

    @SuppressWarnings("unchecked")
    private void visitPredicateObjectList(
        TurtleParser.PredicateObjectListContext ctx,
        Pair<Resource, NodeInfo> subject
    ) {
        List<TurtleParser.VerbContext> verbs = ctx.verb();
        List<TurtleParser.ObjectListContext> objectLists = ctx.objectList();

        for (int i = 0; i < verbs.size(); i++) {
            Pair<Property, NodeInfo> predicate = (Pair<Property, NodeInfo>) visitVerb(verbs.get(i));
            List<Pair<RDFNode, NodeInfo>> objects = (List<Pair<RDFNode, NodeInfo>>) visitObjectList(objectLists.get(i));

            for (Pair<RDFNode, NodeInfo> obj : objects) {
                Statement stmt = model.createStatement(subject.first(), predicate.first(), obj.first());
                store.getTriples().add(new ProvTriple(stmt, subject.second(), predicate.second(), obj.second()));
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object visitVerb(TurtleParser.VerbContext ctx) {
        if (ctx.iri() != null) {
            Pair<Resource, NodeInfo> iriPair = (Pair<Resource, NodeInfo>) visitIri(ctx.iri());
            Property property = model.createProperty(iriPair.first().getURI());
            return new Pair<>(property, iriPair.second());
        } else if ("a".equals(ctx.getText())) {
            NodeInfo nodeInfo = new NodeInfo(
                TurtleNodeKind.TYPE_VERB,
                getStartPoint(ctx.start), getEndPoint(ctx.stop), null
            );
            return new Pair<>(RDF.type, nodeInfo);
        } else {
            throw new IllegalArgumentException("Unknown verb type: " + ctx.getText());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object visitObjectList(TurtleParser.ObjectListContext ctx) {
        List<Pair<RDFNode, NodeInfo>> result = new ArrayList<>();
        for (TurtleParser.Object_Context objCtx : ctx.object_()) {
            result.add((Pair<RDFNode, NodeInfo>) visitObject_(objCtx));
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object visitObject_(TurtleParser.Object_Context ctx) {
        if (ctx.iri() != null) return visitIri(ctx.iri());
        if (ctx.BlankNode() != null) return visitBlankNodeTerminal(ctx.BlankNode());
        if (ctx.collection() != null) return visitCollection(ctx.collection());
        if (ctx.blankNodePropertyList() != null) return visitBlankNodePropertyList(ctx.blankNodePropertyList());
        if (ctx.literal() != null) return visitLiteral(ctx.literal());
        if (ctx.tripleTerm() != null) throw new UnsupportedOperationException("RDF-star / RDF 1.2 triple terms are not supported");
        if (ctx.reifiedTriple() != null) throw new UnsupportedOperationException("RDF-star / RDF 1.2 reified triples are not supported");
        throw new IllegalArgumentException("Unknown object type: " + ctx.getText());
    }

    @Override
    public Object visitLiteral(TurtleParser.LiteralContext ctx) {
        if (ctx.rdfLiteral() != null) return visitRdfLiteral(ctx.rdfLiteral());
        if (ctx.NumericLiteral() != null) return visitNumericLiteral(ctx.NumericLiteral());
        if (ctx.BooleanLiteral() != null) return visitBooleanLiteral(ctx.BooleanLiteral());
        throw new IllegalArgumentException("Unknown literal type");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object visitRdfLiteral(TurtleParser.RdfLiteralContext ctx) {
        Pair<String, Integer> stringInfo = (Pair<String, Integer>) visitString(ctx.string());
        String stringValue = stringInfo.first();
        int quoteSize = stringInfo.second();

        TerminalNode langDirCtx = ctx.LANG_DIR();
        TurtleParser.IriContext iriCtx = ctx.iri();
        Literal literal;

        if (langDirCtx != null) {
            String langTag = langDirCtx.getText().substring(1); // Remove @
            literal = model.createLiteral(stringValue, langTag);
        } else if (iriCtx != null) {
            Resource datatype = ((Pair<Resource, NodeInfo>) visitIri(iriCtx)).first();
            literal = model.createTypedLiteral(stringValue, datatype.getURI());
        } else {
            literal = model.createTypedLiteral(stringValue);
        }

        TurtleNodeKind kind;
        TerminalNode firstChild = (TerminalNode) ctx.string().getChild(0);
        int symbolType = firstChild.getSymbol().getType();
        
        if (symbolType == TurtleParser.STRING_LITERAL_QUOTE) {
            kind = TurtleNodeKind.STRING_LITERAL_QUOTE;
        } else if (symbolType == TurtleParser.STRING_LITERAL_SINGLE_QUOTE) {
            kind = TurtleNodeKind.STRING_LITERAL_SINGLE_QUOTE;
        } else if (symbolType == TurtleParser.STRING_LITERAL_LONG_QUOTE) {
            kind = TurtleNodeKind.STRING_LITERAL_LONG_QUOTE;
        } else if (symbolType == TurtleParser.STRING_LITERAL_LONG_SINGLE_QUOTE) {
            kind = TurtleNodeKind.STRING_LITERAL_LONG_SINGLE_QUOTE;
        } else {
            kind = TurtleNodeKind.STRING_LITERAL_QUOTE;
        }

        Point startPoint = getStartPoint(ctx.start);
        Point endPoint = getEndPoint(ctx.stop);
        
        Point rdfStart = null;
        if (ctx.string().start != null) {
            rdfStart = getStartPoint(ctx.string().start).plus(new Point(0, quoteSize));
        }
        Point rdfEnd = null;
        if (ctx.string().stop != null) {
            Point stopEnd = getEndPoint(ctx.string().stop);
            if (stopEnd != null) {
                rdfEnd = stopEnd.minus(new Point(0, quoteSize));
            }
        }

        NodeInfo nodeInfo = new NodeInfo(
            kind,
            startPoint,
            endPoint,
            rdfStart,
            rdfEnd,
            null
        );
        return new Pair<>(literal, nodeInfo);
    }

    @Override
    public Object visitString(TurtleParser.StringContext ctx) {
        String text = ctx.getText();
        if (text.startsWith("\"\"\"") && text.endsWith("\"\"\"")) {
            return new Pair<>(text.substring(3, text.length() - 3), 3);
        } else if (text.startsWith("'''") && text.endsWith("'''")) {
            return new Pair<>(text.substring(3, text.length() - 3), 3);
        } else if (text.startsWith("\"") && text.endsWith("\"")) {
            return new Pair<>(text.substring(1, text.length() - 1), 1);
        } else if (text.startsWith("'") && text.endsWith("'")) {
            return new Pair<>(text.substring(1, text.length() - 1), 1);
        } else {
            return new Pair<>(text, 0);
        }
    }

    private Pair<Literal, NodeInfo> visitNumericLiteral(TerminalNode terminalNode) {
        Token token = terminalNode.getSymbol();
        String text = token.getText() != null ? token.getText() : "";

        Literal literal;
        TurtleNodeKind kind;

        if (text.contains("e") || text.contains("E")) {
            literal = model.createTypedLiteral(Double.parseDouble(text));
            kind = TurtleNodeKind.DOUBLE_LITERAL;
        } else if (text.contains(".")) {
            literal = model.createTypedLiteral(Double.parseDouble(text)); // TODO add decimal
            kind = TurtleNodeKind.DECIMAL_LITERAL;
        } else {
            literal = model.createTypedLiteral(Integer.parseInt(text));
            kind = TurtleNodeKind.INTEGER_LITERAL;
        }

        return new Pair<>(literal, new NodeInfo(kind, getStartPoint(token), getEndPoint(token), null));
    }

    private Pair<Literal, NodeInfo> visitBooleanLiteral(TerminalNode terminalNode) {
        Token token = terminalNode.getSymbol();
        String text = token.getText();
        Literal literal = model.createTypedLiteral(Boolean.parseBoolean(text));
        NodeInfo nodeInfo = new NodeInfo(TurtleNodeKind.BOOLEAN_LITERAL, getStartPoint(token), getEndPoint(token), null);
        return new Pair<>(literal, nodeInfo);
    }

    public record Pair<A, B>(A first, B second) {}
}
