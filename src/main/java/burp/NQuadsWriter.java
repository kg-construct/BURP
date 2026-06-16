package burp;

import burp.model.RdfStatement;
import burp.model.rdf.*;
import burp.vocabularies.RML;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class NQuadsWriter {

    public static void write(OutputStream output, List<RdfStatement> statements, Charset charset) {
        if (charset == null) {
            charset = StandardCharsets.UTF_8;
        }
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, charset));
        try {
            for (RdfStatement statement : statements) {
                writer.append(serializeStatement(statement)).append('\n');
            }
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static String serializeStatement(RdfStatement statement) {
        String subject = serializeSubject(statement.getSubject());
        String predicate = serializeIRI(statement.getPredicate());
        String obj = serializeObject(statement.getObject());
        String graph = serializeGraph(statement.getGraph());
        if (graph == null) {
            return subject + " " + predicate + " " + obj + " .";
        } else {
            return subject + " " + predicate + " " + obj + " " + graph + " .";
        }
    }

    private static String serializeSubject(BlankNodeOrIRI subject) {
        if (subject instanceof IRITerm) {
            return serializeIRI((IRITerm) subject);
        } else if (subject instanceof BlankNodeTerm) {
            return serializeBlankNode((BlankNodeTerm) subject);
        } else {
            throw new IllegalArgumentException("Unsupported subject term " + subject);
        }
    }

    private static String serializeObject(Term obj) {
        return switch (obj) {
            case IRITerm iriTerm -> serializeIRI(iriTerm);
            case BlankNodeTerm blankNodeTerm -> serializeBlankNode(blankNodeTerm);
            case LiteralTerm literalTerm -> serializeLiteral(literalTerm);
            case null, default -> throw new IllegalArgumentException("Unsupported object term " + obj);
        };
    }

    private static String serializeGraph(IRITerm graph) {
        if (graph == null || RML.defaultGraph.getURI().equals(graph.uri())) return null;
        return serializeIRI(graph);
    }

    private static String serializeIRI(IRITerm iri) {
        return "<" + iri.uri() + ">";
    }

    private static String serializeBlankNode(BlankNodeTerm blankNode) {
        String label = blankNode.id().startsWith("_:") ? blankNode.id() : "_:" + blankNode.id();
        return normalizeBlankNodeLabel(label);
    }

    private static boolean isValidBlankNodeLabel(String label) {
        if (!label.startsWith("_:") || label.length() < 3) return false;

        String id = label.substring(2);

        char firstChar = id.charAt(0);
        if (!isValidFirstBlankNodeChar(firstChar)) return false;

        if (id.length() == 1) return true;

        for (int i = 1; i < id.length(); i++) {
            if (!isValidEncodableBlankNodeChar(id.charAt(i))) return false;
        }

        return true;
    }

    private static boolean isValidFirstBlankNodeChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static boolean isValidEncodableBlankNodeChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
    }

    private static String normalizeBlankNodeLabel(String label) {
        if (isValidBlankNodeLabel(label)) {
            return label;
        } else {
            String id = label.substring(2);
            StringBuilder encoded = new StringBuilder("_:");

            for (int i = 0; i < id.length(); i++) {
                char ch = id.charAt(i);
                if (isValidEncodableBlankNodeChar(ch)) {
                    encoded.append(ch);
                } else {
                    encoded.append('_');
                    String hex = Integer.toHexString((int) ch);
                    if (hex.length() < 2) encoded.append('0');
                    encoded.append(hex);
                }
            }
            return encoded.toString();
        }
    }

    private static String serializeLiteral(LiteralTerm literal) {
        String lexicalForm = escapeLiteral(literal.value());
        String suffix = "";
        if (literal.language() != null) {
            suffix = "@" + literal.language();
        } else if (literal.datatype() != null && !literal.datatype().uri().equals("http://www.w3.org/2001/XMLSchema#string")) {
            suffix = "^^" + serializeIRI(literal.datatype());
        }
        return "\"" + lexicalForm + "\"" + suffix;
    }

    static String escapeLiteral(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            int chCode = value.codePointAt(i);
            switch (ch) {
                case '\\': escaped.append("\\\\"); break;
                case '"': escaped.append("\\\""); break;
                case '\n': escaped.append("\\n"); break;
                case '\r': escaped.append("\\r"); break;
                case '\t': escaped.append("\\t"); break;
                case '\b': escaped.append("\\b"); break;
                case '\u000C': escaped.append("\\f"); break;
                default:
                    if (chCode < 0x20) {
                        escaped.append("\\u");
                        String hex = Integer.toHexString(ch);
                        escaped.repeat("0", 4 - hex.length());
                        escaped.append(hex);
                    } else {
                        escaped.append(ch);
                    }
                    break;
            }
        }
        return escaped.toString();
    }
}
