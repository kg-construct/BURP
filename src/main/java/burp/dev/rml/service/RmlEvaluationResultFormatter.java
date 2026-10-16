package burp.dev.rml.service;

import burp.model.*;
import burp.model.rdf.BlankNodeTerm;
import burp.model.rdf.IRITerm;
import burp.model.rdf.LiteralTerm;
import burp.model.rdf.Term;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Utility for formatting evaluated RDF terms and statements with prefix-aware URI compaction.
 */
@NullMarked
public final class RmlEvaluationResultFormatter {

    private RmlEvaluationResultFormatter() {
    }

    public static @Nullable String formatSummary(@Nullable RmlEvaluationResult rdf, @Nullable Map<String, String> prefixMap) {
        switch (rdf) {
            case null -> {
                return null;
            }
            case RdfStatement statement -> {
                String s = formatTerm(statement.getSubject(), prefixMap, "?s");
                String p = formatTerm(statement.getPredicate(), prefixMap, "?p");
                String o = formatTerm(statement.getObject(), prefixMap, "?o");
                return s + " " + p + " " + o + " .";
            }
            case RdfPredicateObject po -> {
                String p = formatTerm(po.predicate(), prefixMap, "?p");
                String o = formatTerm(po.object(), prefixMap, "?o");
                String g = po.graph() != null ? " " + formatTerm(po.graph(), prefixMap, "") : "";
                return p + " " + o + g + " ;";
            }
            case Term term -> {
                return formatTerm(term, prefixMap, "");
            }
            case RawValue rv -> {
                return formatRawValue(rv.value(), prefixMap);
            }
            default -> {
                return rdf.toString();
            }
        }
    }

    public static String formatSummaryAlways(@Nullable RmlEvaluationResult rdf, @Nullable Map<String, String> prefixMap) {
        var formatted = formatSummary(rdf, prefixMap);
        return formatted == null ? "null" : formatted;
    }

    public static String formatRawValue(@Nullable Object val, @Nullable Map<String, String> prefixMap) {
        return switch (val) {
            case null -> "";
            case Boolean b -> b.toString();
            case Number n -> n.toString();
            case String s -> "\"" + s + "\"";
            case Term t -> formatTerm(t, prefixMap, "");
            default -> "`" + val + "`";
        };
    }

    public static String formatTerm(@Nullable Term term, @Nullable Map<String, String> prefixMap, String defaultVal) {
        switch (term) {
            case null -> {
                return defaultVal;
            }
            case IRITerm iriTerm -> {
                return compactIri(iriTerm.uri(), prefixMap);
            }
            case LiteralTerm lit -> {
                String dtUri = (lit.datatype() != null) ? lit.datatype().uri() : null;
                if ("http://www.w3.org/2001/XMLSchema#boolean".equals(dtUri)) {
                    return lit.value();
                }
                String dt = (dtUri != null && !"http://www.w3.org/2001/XMLSchema#string".equals(dtUri))
                        ? "^^" + compactIri(dtUri, prefixMap)
                        : "";
                String lang = (lit.language() != null && !lit.language().isEmpty())
                        ? "@" + lit.language()
                        : "";
                return "\"" + lit.value() + "\"" + (lang.isEmpty() ? dt : lang);
            }
            case BlankNodeTerm bn -> {
                return "_:" + bn.id();
            }
            case burp.model.rdf.CollectionOrContainerTerm cc -> {
                String prefix = "";
                if (!cc.isIdGenerated() && cc.getId() != null) {
                    prefix = formatTerm(cc.getId(), prefixMap, "") + " ";
                }
                String elementsFormatted = cc.getElements().stream()
                        .map(el -> formatTerm(el, prefixMap, ""))
                        .collect(java.util.stream.Collectors.joining(cc instanceof burp.model.rdf.RdfListTerm ? " " : ", "));

                return switch (cc) {
                    case burp.model.rdf.RdfListTerm ignored -> prefix + "( " + elementsFormatted + " )";
                    case burp.model.rdf.RdfBagTerm ignored -> prefix + "Bag[ " + elementsFormatted + " ]";
                    case burp.model.rdf.RdfSeqTerm ignored -> prefix + "Seq[ " + elementsFormatted + " ]";
                    case burp.model.rdf.RdfAltTerm ignored -> prefix + "Alt[ " + elementsFormatted + " ]";
                };
            }
            default -> {
                return term.toString();
            }
        }
    }

    public static final Map<String, String> DEFAULT_PREFIXES = Map.of(
            "xsd", "http://www.w3.org/2001/XMLSchema#",
            "rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            "rdfs", "http://www.w3.org/2000/01/rdf-schema#",
            "rml", burp.vocabularies.Rml.NS
    );

    public static String compactIri(@Nullable String iri, @Nullable Map<String, String> prefixMap) {
        if (iri == null) {
            return "<>";
        }
        org.apache.jena.shared.PrefixMapping pm = org.apache.jena.shared.PrefixMapping.Factory.create();
        pm.setNsPrefixes(DEFAULT_PREFIXES);
        if (prefixMap != null && !prefixMap.isEmpty()) {
            pm.setNsPrefixes(prefixMap);
        }
        String qname = pm.qnameFor(iri);
        if (qname != null) {
            return qname;
        }
        if (prefixMap != null && !prefixMap.isEmpty()) {
            String prefix = compactAsPrefixNameOrNull(iri, prefixMap);
            if (prefix != null) return prefix;
        }
        String prefix = compactAsPrefixNameOrNull(iri, DEFAULT_PREFIXES);
        if (prefix != null) return prefix;
        return "<" + iri + ">";
    }

    private static @Nullable String compactAsPrefixNameOrNull(String iri, Map<String, String> prefixMap) {
        for (Map.Entry<String, String> entry : prefixMap.entrySet()) {
            String prefix = entry.getKey();
            String ns = entry.getValue();
            if (!ns.isEmpty() && iri.startsWith(ns)) {
                String local = iri.substring(ns.length());
                return prefix.isEmpty() ? ":" + local : prefix + ":" + local;
            }
        }
        return null;
    }
}
