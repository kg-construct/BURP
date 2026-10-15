package burp.parse.turtleprov;

import burp.reporting.*;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.irix.IRIx;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProvTurtleVisitor extends TurtleBaseVisitor<Object> {
    public static List<PointRange> retrieveTurtleLocation(List<? extends RDFPointer> o) {
        if (!(o instanceof List<?> sourceStatements)) {
            return Collections.emptyList();
        }
        if (sourceStatements.isEmpty()) {
            return Collections.emptyList();
        }
        List<PointRange> locations = new ArrayList<>();
        for (var item : sourceStatements) {
            if (item instanceof RDFGraphPointer gp) {
                var stmt = gp.stmt();
                if (stmt == null || stmt.getModel() == null) {
                    continue;
                }
                var infos = RDF12Converter.fromAnnotations(stmt, stmt.getModel());
                if (gp instanceof StatementParts sp) {
                    if (sp.subject() && infos.subjectInfo() != null && infos.subjectInfo().start() != null) {
                        locations.add(new PointRange(infos.subjectInfo().start(), infos.subjectInfo().end()));
                    }
                    if (sp.predicate() && infos.predicateInfo() != null && infos.predicateInfo().start() != null) {
                        locations.add(new PointRange(infos.predicateInfo().start(), infos.predicateInfo().end()));
                    }
                    if (sp.object() && infos.objectInfo() != null && infos.objectInfo().start() != null) {
                        locations.add(new PointRange(infos.objectInfo().start(), infos.objectInfo().end()));
                    }
                } else if (gp instanceof LiteralPart lp) {
                    if (infos.objectInfo() != null) {
                        var info = infos.objectInfo();
                        Point startPt = info.rdfLiteralStringStart() != null ? info.rdfLiteralStringStart() : info.start();
                        Point literalEnd = lp.objectRange().end();
                        Point relStart = lp.objectRange().start();
                        Point relEnd = literalEnd;
                        if (info.offsetMapper() != null) {
                            if (relStart != null && relStart.line() == 0) {
                                relStart = new Point(0, info.offsetMapper().toRawOffset(relStart.column()));
                            }
                            if (relEnd != null && relEnd.line() == 0) {
                                relEnd = new Point(0, info.offsetMapper().toRawOffset(relEnd.column()));
                            }
                        }
                        Point newStart = startPt.plus(relStart);
                        Point newEnd = relEnd != null ? startPt.plus(relEnd) : info.rdfLiteralStringEnd();
                        if (newEnd != null) {
                            locations.add(new PointRange(newStart, newEnd));
                        }
                    }
                }
            }
        }
        return locations;
    }

    private final ProvStore store = new ProvStore();
    private final @Nullable String documentUri;

    public ProvTurtleVisitor() {
        this(null);
    }

    public ProvTurtleVisitor(@Nullable String documentUri) {
        this.documentUri = documentUri;
        this.store.setDocumentUri(documentUri);
    }

    public ProvStore getStore() {
        return store;
    }

    private final Model model = ModelFactory.createDefaultModel();
    private final Map<String, Resource> blankNodeMap = new HashMap<>();

    private Resource createBlankNode(Token token) {
        if (token != null) {
            String id = "[" + token.getLine() + ":" + (token.getCharPositionInLine() + 1) + "]";
            return createBlankNode(id);
        }
        return createBlankNode((String) null);
    }

    private Resource createBlankNode(String id) {
        if (id != null) {
            String fullId = (documentUri != null && !id.startsWith(documentUri)) ? (documentUri + "#" + id) : id;
            return model.createResource(new AnonId(fullId));
        }
        return model.createResource(new AnonId());
    }

    private static final Pattern prefixedNamePattern = Pattern.compile("^(.*?):(.*)$");
    private static final Pattern blankNodeLabelPattern = Pattern.compile("^_:(.+)$");
    private String baseURI = null;

    private String resolveRelativeIRI(String base, String rel) {
        try {
            return IRIx.create(base).resolve(rel).toString();
        } catch (Exception e) {
            return rel;
        }
    }

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

    private PointRange getRange(ParserRuleContext ctx) {
        if (ctx == null || ctx.getStart() == null) return null;
        Point start = getStartPoint(ctx.getStart());
        Point end = ctx.getStop() != null ? getEndPoint(ctx.getStop()) : getEndPoint(ctx.getStart());
        return new PointRange(start, end);
    }

    private PointRange getRange(Token token) {
        if (token == null) return null;
        return new PointRange(getStartPoint(token), getEndPoint(token));
    }

    private PointRange getRange(TerminalNode node) {
        if (node == null || node.getSymbol() == null) return null;
        return getRange(node.getSymbol());
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

    public Object makeRegisterPrefix(TerminalNode prefix, TerminalNode iriRef, boolean isSparql) {
        String prefixText = prefix.getText();
        String prefixNs = prefixText.substring(0, prefixText.length() - 1);
        String iriText = iriRef.getText();
        String iri = iriText.substring(1, iriText.length() - 1);
        iri = unescapeIri(iri);
        if (baseURI != null) {
            iri = resolveRelativeIRI(baseURI, iri);
        }
        store.getPrefixes().put(prefixNs, iri);
        return null;
    }

    @Override
    public Object visitPrefixID(TurtleParser.PrefixIDContext ctx) {
        return makeRegisterPrefix(ctx.PNAME_NS(), ctx.IRIREF(), false);
    }

    @Override
    public Object visitSparqlPrefix(TurtleParser.SparqlPrefixContext ctx) {
        return makeRegisterPrefix(ctx.PNAME_NS(), ctx.IRIREF(), true);
    }

    @Override
    public Object visitBase(TurtleParser.BaseContext ctx) {
        String baseText = ctx.IRIREF().getText();
        String base = baseText.substring(1, baseText.length() - 1);
        base = unescapeIri(base);
        if (baseURI != null) {
            this.baseURI = resolveRelativeIRI(baseURI, base);
        } else {
            this.baseURI = base;
        }
        return null;
    }

    @Override
    public Object visitSparqlBase(TurtleParser.SparqlBaseContext ctx) {
        String baseText = ctx.IRIREF().getText();
        String base = baseText.substring(1, baseText.length() - 1);
        base = unescapeIri(base);
        if (baseURI != null) {
            this.baseURI = resolveRelativeIRI(baseURI, base);
        } else {
            this.baseURI = base;
        }
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
        throw new TurtleProvException("Unknown subject type", getRange(ctx));
    }

    public static String unescapeLocalName(String s) {
        if (s == null) return null;
        return s.replaceAll("\\\\(.)", "$1");
    }

    private static final Pattern ESCAPE_PATTERN = Pattern.compile("\\\\(u([0-9a-fA-F]{0,4})|U([0-9a-fA-F]{0,8})|.?)");

    public record UnescapeResult(String unescaped, OffsetMapper offsetMapper) {}

    public static UnescapeResult unescapeWithOffsets(String s, boolean isIri) {
        if (s == null) return new UnescapeResult(null, OffsetMapper.identity());
        Matcher m = ESCAPE_PATTERN.matcher(s);
        StringBuilder sb = new StringBuilder(s.length());
        OffsetMapper.Builder builder = OffsetMapper.builder();
        int lastAppendPosition = 0;

        while (m.find()) {
            sb.append(s, lastAppendPosition, m.start());

            String esc = m.group(1);
            if (esc == null || esc.isEmpty()) {
                throw new IllegalArgumentException(isIri ? "Trailing backslash in IRI" : "Trailing backslash in string");
            }
            int rawLength = m.end() - m.start();
            int decStart = sb.length();

            char first = esc.charAt(0);
            if (first == 'u') {
                if (esc.length() < 5) {
                    throw new IllegalArgumentException("Incomplete \\u unicode escape");
                }
                int cp = Integer.parseInt(esc.substring(1), 16);
                sb.append((char) cp);
            } else if (first == 'U') {
                if (esc.length() < 9) {
                    throw new IllegalArgumentException("Incomplete \\U unicode escape");
                }
                int cp = Integer.parseInt(esc.substring(1), 16);
                sb.append(Character.toChars(cp));
            } else {
                if (isIri) {
                    throw new IllegalArgumentException("Invalid escape in IRI: \\" + first);
                }
                char replacement = switch (first) {
                    case 't' -> '\t';
                    case 'b' -> '\b';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 'f' -> '\f';
                    case '"' -> '"';
                    case '\'' -> '\'';
                    case '\\' -> '\\';
                    default -> throw new IllegalArgumentException("Invalid escape sequence: \\" + first);
                };
                sb.append(replacement);
            }

            int decLength = sb.length() - decStart;
            builder.recordEscape(sb.length(), rawLength, decLength);
            lastAppendPosition = m.end();
        }
        sb.append(s, lastAppendPosition, s.length());
        return new UnescapeResult(sb.toString(), builder.build());
    }

    private static String unescape(String s, boolean isIri) {
        return unescapeWithOffsets(s, isIri).unescaped();
    }

    public static String unescapeIri(String s) {
        return unescape(s, true);
    }

    private static NodeInfo nodeInfo(TurtleNodeKind kind, Point start, Point end, @Nullable String blankNodeId) {
        return new NodeInfo(kind, start, end, null, null, blankNodeId, null);
    }

    @Override
    public Object visitIri(TurtleParser.IriContext ctx) {
        TerminalNode irirefCtx = ctx.IRIREF();
        TurtleParser.PrefixedNameContext prefixedNameCtx = ctx.prefixedName();

        if (irirefCtx != null) {
            Token token = irirefCtx.getSymbol();
            String iriText = token.getText();
            String iri = iriText.substring(1, iriText.length() - 1);
            iri = unescapeIri(iri);
            if (baseURI != null) {
                iri = resolveRelativeIRI(baseURI, iri);
            }
            Resource resource = ResourceFactory.createResource(iri);
            NodeInfo nodeInfo = nodeInfo(
                    TurtleNodeKind.IRIREF,
                    getStartPoint(token),
                    getEndPoint(token),
                    null
            );
            return new Pair<>(resource, nodeInfo);
        } else if (prefixedNameCtx != null) {
            String prefixedName = prefixedNameCtx.getText() != null ? prefixedNameCtx.getText() : "";
            Matcher match = prefixedNamePattern.matcher(prefixedName);

            if (match.matches()) {
                String prefix = match.group(1) != null ? match.group(1) : "";
                String localName = match.group(2);
                if (localName != null) {
                    localName = unescapeLocalName(localName);
                } else {
                    localName = "";
                }
                String namespace = store.getPrefixes().get(prefix);
                if (namespace == null)
                    throw new TurtleProvException("Unknown prefix: " + prefix, getRange(prefixedNameCtx));
                Resource resource = ResourceFactory.createResource(namespace + localName);
                NodeInfo nodeInfo = nodeInfo(TurtleNodeKind.PREFIXED_NAME, getStartPoint(prefixedNameCtx.getStart()), getEndPoint(prefixedNameCtx.getStop()), null);
                return new Pair<>(resource, nodeInfo);
            } else {
                throw new TurtleProvException("Invalid prefixed name: " + prefixedName, getRange(prefixedNameCtx));
            }
        } else {
            throw new TurtleProvException("Unknown IRI type", getRange(ctx));
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
                NodeInfo nodeInfo = nodeInfo(
                        TurtleNodeKind.BLANK_NODE_LABEL,
                        getStartPoint(token), getEndPoint(token), label
                );
                return new Pair<>(resource, nodeInfo);
            } else {
                throw new TurtleProvException("Invalid blank node label: " + text, getRange(token));
            }
        } else if (text.startsWith("[") && text.endsWith("]")) {
            Resource resource = createBlankNode(token);
            NodeInfo nodeInfo = nodeInfo(
                    TurtleNodeKind.ANONYMOUS_BLANK_NODE,
                    getStartPoint(token), getEndPoint(token), null
            );
            return new Pair<>(resource, nodeInfo);
        } else {
            throw new TurtleProvException("Unknown blank node format: " + text, getRange(token));
        }
    }

    public Resource assembleList(List<RDFNode> list, Token startToken) {
        if (list.isEmpty()) {
            return RDF.nil;
        } else {
            List<Resource> blankNodes = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                String id = "[" + startToken.getLine() + ":" + (startToken.getCharPositionInLine() + 1) + (i > 0 ? ":" + i : "") + "]";
                blankNodes.add(createBlankNode(id));
            }
            for (int i = 0; i < list.size(); i++) {
                Resource node = blankNodes.get(i);
                RDFNode value = list.get(i);
                store.getTriples().add(new ProvTriple(model.createStatement(node, RDF.first, value), null, null, null, documentUri));
            }
            for (int i = 0; i < blankNodes.size() - 1; i++) {
                Resource current = blankNodes.get(i);
                Resource next = blankNodes.get(i + 1);
                store.getTriples().add(new ProvTriple(model.createStatement(current, RDF.rest, next), null, null, null, documentUri));
            }
            store.getTriples().add(new ProvTriple(model.createStatement(blankNodes.getLast(), RDF.rest, (Resource) RDF.nil), null, null, null, documentUri));
            return blankNodes.getFirst();
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
        Resource collectionStart = assembleList(items, ctx.start);
        NodeInfo nodeInfo = nodeInfo(
                TurtleNodeKind.COLLECTION,
                getStartPoint(ctx.start), getEndPoint(ctx.stop), null
        );

        return new Pair<>(collectionStart, nodeInfo);
    }

    @Override
    public Object visitBlankNodePropertyList(TurtleParser.BlankNodePropertyListContext ctx) {
        Resource resource = createBlankNode(ctx.start);
        NodeInfo nodeInfo = nodeInfo(
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
                store.getTriples().add(new ProvTriple(stmt, subject.second(), predicate.second(), obj.second(), documentUri));
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
            NodeInfo nodeInfo = nodeInfo(
                    TurtleNodeKind.TYPE_VERB,
                    getStartPoint(ctx.start), getEndPoint(ctx.stop), null
            );
            return new Pair<>(RDF.type, nodeInfo);
        } else {
            throw new TurtleProvException("Unknown verb type: " + ctx.getText(), getRange(ctx));
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
    public Object visitObject_(TurtleParser.Object_Context ctx) {
        if (ctx.iri() != null) return visitIri(ctx.iri());
        if (ctx.BlankNode() != null) return visitBlankNodeTerminal(ctx.BlankNode());
        if (ctx.collection() != null) return visitCollection(ctx.collection());
        if (ctx.blankNodePropertyList() != null) return visitBlankNodePropertyList(ctx.blankNodePropertyList());
        if (ctx.literal() != null) return visitLiteral(ctx.literal());
        if (ctx.tripleTerm() != null)
            throw new TurtleProvException("RDF-star / RDF 1.2 triple terms are not supported", getRange(ctx));
        if (ctx.reifiedTriple() != null)
            throw new TurtleProvException("RDF-star / RDF 1.2 reified triples are not supported", getRange(ctx));
        throw new TurtleProvException("Unknown object type: " + ctx.getText(), getRange(ctx));
    }

    @Override
    public Object visitLiteral(TurtleParser.LiteralContext ctx) {
        if (ctx.rdfLiteral() != null) return visitRdfLiteral(ctx.rdfLiteral());
        if (ctx.NumericLiteral() != null) return visitNumericLiteral(ctx.NumericLiteral());
        if (ctx.BooleanLiteral() != null) return visitBooleanLiteral(ctx.BooleanLiteral());
        throw new TurtleProvException("Unknown literal type", getRange(ctx));
    }

    public record StringLiteralResult(String content, int delimiterLength, OffsetMapper offsetMapper) {}

    @Override
    @SuppressWarnings("unchecked")
    public Object visitRdfLiteral(TurtleParser.RdfLiteralContext ctx) {
        StringLiteralResult stringInfo = (StringLiteralResult) visitString(ctx.string());
        String stringValue = stringInfo.content();
        int quoteSize = stringInfo.delimiterLength();
        OffsetMapper offsetMapper = stringInfo.offsetMapper();

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
                null,
                offsetMapper
        );
        return new Pair<>(literal, nodeInfo);
    }

    public static String unescapeRdfLiteralString(String s) {
        return unescape(s, false);
    }

    @Override
    public Object visitString(TurtleParser.StringContext ctx) {
        String text = ctx.getText();
        String content;
        int delimiterLength;
        if (ctx.STRING_LITERAL_LONG_QUOTE() != null || ctx.STRING_LITERAL_LONG_SINGLE_QUOTE() != null) {
            content = text.substring(3, text.length() - 3);
            delimiterLength = 3;
        } else {
            assert ctx.STRING_LITERAL_QUOTE() != null || ctx.STRING_LITERAL_SINGLE_QUOTE() != null;
            content = text.substring(1, text.length() - 1);
            delimiterLength = 1;
        }
        UnescapeResult result = unescapeWithOffsets(content, false);
        return new StringLiteralResult(result.unescaped(), delimiterLength, result.offsetMapper());
    }

    private Pair<Literal, NodeInfo> visitNumericLiteral(TerminalNode terminalNode) {
        Token token = terminalNode.getSymbol();
        String text = token.getText() != null ? token.getText() : "";

        Literal literal;
        TurtleNodeKind kind;

        if (text.contains("e") || text.contains("E")) {
            literal = model.createTypedLiteral(text, XSDDatatype.XSDdouble);
            kind = TurtleNodeKind.DOUBLE_LITERAL;
        } else if (text.contains(".")) {
            literal = model.createTypedLiteral(text, XSDDatatype.XSDdecimal);
            kind = TurtleNodeKind.DECIMAL_LITERAL;
        } else {
            literal = model.createTypedLiteral(text, XSDDatatype.XSDinteger);
            kind = TurtleNodeKind.INTEGER_LITERAL;
        }

        return new Pair<>(literal, nodeInfo(kind, getStartPoint(token), getEndPoint(token), null));
    }

    private Pair<Literal, NodeInfo> visitBooleanLiteral(TerminalNode terminalNode) {
        Token token = terminalNode.getSymbol();
        String text = token.getText();
        Literal literal = model.createTypedLiteral(Boolean.parseBoolean(text));
        NodeInfo nodeInfo = nodeInfo(TurtleNodeKind.BOOLEAN_LITERAL, getStartPoint(token), getEndPoint(token), null);
        return new Pair<>(literal, nodeInfo);
    }

    public record Pair<A, B>(A first, B second) {
    }
}
