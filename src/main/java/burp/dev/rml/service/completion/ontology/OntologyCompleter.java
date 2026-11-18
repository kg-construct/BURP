package burp.dev.rml.service.completion.ontology;

import burp.dev.rml.service.RmlEvaluationResultFormatter;
import burp.dev.rml.service.ontology.OntologyRegistry;
import burp.dev.rml.service.ontology.PropertyCandidate;
import burp.dev.rml.service.ontology.PropertyConstraint;
import burp.model.PredicateMap;
import burp.model.PredicateObjectMap;
import burp.model.RDFNodeConstant;
import burp.model.TriplesMap;
import burp.model.rdf.IRITerm;
import org.apache.jena.rdf.model.Resource;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;

/// Provides ranked fuzzy autocompletion for classes, predicates, and datatypes based on target ontologies.
@NullMarked
public class OntologyCompleter {

    public static final int MAX_PROPOSITIONS = 50;

    public List<CompletionItem> completePredicates(
            TriplesMap enclosingTriplesMap,
            OntologyRegistry registry,
            Map<String, String> prefixMap,
            String effectivePrefix,
            @Nullable Range replaceRange
    ) {
        Set<Resource> classes = new LinkedHashSet<>();
        if (enclosingTriplesMap.subjectMap != null) {
            classes.addAll(enclosingTriplesMap.subjectMap.classes);
        }

        List<PropertyCandidate> candidates = registry.getApplicableProperties(classes, prefixMap);

        Set<String> alreadyPresentUris = new HashSet<>();
        for (PredicateObjectMap pom : enclosingTriplesMap.predicateObjectMaps) {
            for (PredicateMap pm : pom.predicateMaps) {
                if (pm.expression instanceof RDFNodeConstant c && c.constant instanceof IRITerm iriTerm) {
                    alreadyPresentUris.add(iriTerm.uri());
                }
            }
        }

        record ScoredCandidate(
                PropertyCandidate candidate,
                int fuzzyScore,
                boolean isAlreadyPresent
        ) {}

        List<ScoredCandidate> scored = new ArrayList<>();
        for (PropertyCandidate pc : candidates) {
            String propUri = pc.property().getURI();
            boolean alreadyPresent = alreadyPresentUris.contains(propUri);

            int score = calculateFuzzyScore(effectivePrefix, pc.compactIri(), pc.label());
            if (!effectivePrefix.isEmpty() && score < 0) {
                continue;
            }
            scored.add(new ScoredCandidate(pc, score, alreadyPresent));
        }

        // Sort: 1. Fuzzy score descending, 2. Already-present penalty, 3. Inheritance distance, 4. Label
        scored.sort((a, b) -> {
            if (!effectivePrefix.isEmpty()) {
                int scoreComp = Integer.compare(b.fuzzyScore(), a.fuzzyScore());
                if (scoreComp != 0) return scoreComp;
            }
            int presentComp = Boolean.compare(a.isAlreadyPresent(), b.isAlreadyPresent());
            if (presentComp != 0) return presentComp;

            int distComp = Integer.compare(a.candidate().inheritanceDistance(), b.candidate().inheritanceDistance());
            if (distComp != 0) return distComp;

            return a.candidate().compactIri().compareTo(b.candidate().compactIri());
        });

        List<CompletionItem> items = new ArrayList<>();
        int count = 0;
        for (ScoredCandidate sc : scored) {
            if (count++ >= MAX_PROPOSITIONS) break;

            PropertyCandidate pc = sc.candidate();
            CompletionItem item = new CompletionItem(pc.compactIri());
            item.setKind(CompletionItemKind.Property);

            StringBuilder detail = new StringBuilder();
            if (pc.expectedRange() != null) {
                detail.append("[").append(RmlEvaluationResultFormatter.compactIri(pc.expectedRange().getURI(), prefixMap)).append("]");
            } else if (pc.isObjectProperty()) {
                detail.append("[owl:ObjectProperty]");
            } else if (pc.isDatatypeProperty()) {
                detail.append("[owl:DatatypeProperty]");
            }

            if (pc.inheritanceDistance() > 0 && pc.definedOnClass() != null) {
                detail.append(" from ").append(RmlEvaluationResultFormatter.compactIri(pc.definedOnClass().getURI(), prefixMap));
            }
            if (sc.isAlreadyPresent()) {
                detail.append(" (already in TriplesMap)");
            }
            item.setDetail(detail.toString());

            StringBuilder doc = new StringBuilder();
            doc.append("**Property**: `").append(pc.property().getURI()).append("`\n\n");
            if (pc.label() != null) {
                doc.append("**Label**: ").append(pc.label()).append("\n\n");
            }
            if (pc.comment() != null) {
                doc.append(pc.comment()).append("\n\n");
            }
            if (pc.definedOnClass() != null) {
                doc.append("**Defined on**: `").append(RmlEvaluationResultFormatter.compactIri(pc.definedOnClass().getURI(), prefixMap)).append("`\n\n");
            }
            item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, doc.toString()));
            item.setInsertText(pc.compactIri());
            if (replaceRange != null) {
                item.setTextEdit(Either.forLeft(new TextEdit(replaceRange, pc.compactIri())));
            }
            item.setSortText(String.format("%04d", count));
            items.add(item);
        }

        return items;
    }

    public List<CompletionItem> completeClasses(
            OntologyRegistry registry,
            Map<String, String> prefixMap,
            String effectivePrefix,
            @Nullable Range replaceRange
    ) {
        List<Resource> allClasses = registry.getAllClasses();
        record ScoredClass(Resource cls, String compactIri, int fuzzyScore) {}

        List<ScoredClass> scored = new ArrayList<>();
        for (Resource cls : allClasses) {
            String compact = RmlEvaluationResultFormatter.compactIri(cls.getURI(), prefixMap);
            int score = calculateFuzzyScore(effectivePrefix, compact, null);
            if (!effectivePrefix.isEmpty() && score < 0) {
                continue;
            }
            scored.add(new ScoredClass(cls, compact, score));
        }

        scored.sort((a, b) -> {
            if (!effectivePrefix.isEmpty()) {
                int scoreComp = Integer.compare(b.fuzzyScore(), a.fuzzyScore());
                if (scoreComp != 0) return scoreComp;
            }
            return a.compactIri().compareTo(b.compactIri());
        });

        List<CompletionItem> items = new ArrayList<>();
        int count = 0;
        for (ScoredClass sc : scored) {
            if (count++ >= MAX_PROPOSITIONS) break;

            CompletionItem item = new CompletionItem(sc.compactIri());
            item.setKind(CompletionItemKind.Class);
            item.setDetail("owl:Class");
            item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, "**Class**: `" + sc.cls().getURI() + "`"));
            item.setInsertText(sc.compactIri());
            if (replaceRange != null) {
                item.setTextEdit(Either.forLeft(new TextEdit(replaceRange, sc.compactIri())));
            }
            item.setSortText(String.format("%04d", count));
            items.add(item);
        }

        return items;
    }

    public List<CompletionItem> completeDatatypes(
            Resource predicate,
            OntologyRegistry registry,
            Map<String, String> prefixMap,
            String effectivePrefix,
            @Nullable Range replaceRange
    ) {
        PropertyConstraint constraint = registry.getPropertyConstraint(Collections.emptySet(), predicate);
        Resource expectedRange = constraint.expectedRange();
        if (expectedRange == null) {
            return Collections.emptyList();
        }

        String compact = RmlEvaluationResultFormatter.compactIri(expectedRange.getURI(), prefixMap);
        CompletionItem item = new CompletionItem(compact);
        item.setKind(CompletionItemKind.TypeParameter);
        item.setDetail("Expected range for " + RmlEvaluationResultFormatter.compactIri(predicate.getURI(), prefixMap));
        item.setInsertText(compact);
        if (replaceRange != null) {
            item.setTextEdit(Either.forLeft(new TextEdit(replaceRange, compact)));
        }

        int score = calculateFuzzyScore(effectivePrefix, compact, null);
        if (!effectivePrefix.isEmpty() && score < 0) {
            item.setSortText("9999");
        } else {
            item.setSortText("0000");
        }

        return List.of(item);
    }

    public static int calculateFuzzyScore(String query, String text, @Nullable String label) {
        if (query.isEmpty()) return 0;
        int score1 = fuzzyMatch(query.toLowerCase(), text.toLowerCase());
        int score2 = (label != null) ? fuzzyMatch(query.toLowerCase(), label.toLowerCase()) : -1;
        return Math.max(score1, score2);
    }

    private static int fuzzyMatch(String query, String target) {
        if (target.equals(query)) return 1000;
        if (target.startsWith(query)) return 500 + (100 - Math.min(100, target.length()));

        int qIdx = 0;
        int score = 0;
        int consecutive = 0;

        for (int tIdx = 0; tIdx < target.length() && qIdx < query.length(); tIdx++) {
            char qc = query.charAt(qIdx);
            char tc = target.charAt(tIdx);

            if (qc == tc) {
                qIdx++;
                score += 10;
                consecutive++;
                score += (consecutive * 5);

                // Word boundary bonus
                if (tIdx == 0 || target.charAt(tIdx - 1) == ':' || target.charAt(tIdx - 1) == '/'
                        || target.charAt(tIdx - 1) == '#' || target.charAt(tIdx - 1) == '_') {
                    score += 30;
                }
            } else {
                consecutive = 0;
            }
        }

        if (qIdx < query.length()) {
            return -1; // Subsequence does not match
        }

        return score;
    }
}
