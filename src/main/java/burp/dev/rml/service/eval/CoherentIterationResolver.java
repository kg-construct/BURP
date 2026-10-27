package burp.dev.rml.service.eval;

import burp.dev.NodeSelectorMatcher;
import burp.dev.rml.service.RmlEvaluationResultFormatter;
import burp.dev.rml.service.hint.ExpressionFormatter;
import burp.dev.rml.service.hint.InlineHintsEvaluationMode;
import burp.dev.rml.service.plan.PlanNodeDescriptor;
import burp.dev.rml.service.selector.CursorSelector;
import burp.model.*;
import burp.model.context.EvaluationContext;
import burp.model.join.ParentJoinIndex;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static burp.dev.rml.service.eval.InlineEvaluator.intersects;
import static burp.parse.ValidationMode.LENIENT;

/// Resolves coherent iteration bindings and provenance reasons across joined and independent TriplesMaps.
@NullMarked
public final class CoherentIterationResolver {

    public record AssignedIteration(
            Iteration iteration,
            int index,
            IterationReason reason
    ) {
    }

    public record ResolutionResult(
            Map<TriplesMap, AssignedIteration> assignedIterations,
            Set<TriplesMap> unmatchedTriplesMaps,
            Map<PlanNode, ParentJoinIndex> joinIndexes,
            List<String> traceSummary
    ) {
    }

    private CoherentIterationResolver() {
    }

    public static ResolutionResult resolve(
            MappingDocument mappingDocument,
            EvaluationDataContext effectiveDataContext,
            @Nullable Range requestedRange,
            @Nullable PlanNode anchorNode,
            @Nullable Position cursorPosition,
            RmlEvaluator evaluator,
            InlineHintsEvaluationMode mode,
            @Nullable Integer joinIndexLimit,
            Map<AbstractLogicalSource, List<Iteration>> allSourceIterations,
            @Nullable Map<String, String> prefixMap,
            @Nullable String fallbackBaseIri
    ) {
        Map<TriplesMap, AssignedIteration> assigned = new LinkedHashMap<>();
        Set<TriplesMap> unmatched = new LinkedHashSet<>();
        Map<PlanNode, ParentJoinIndex> joinIndexes = new HashMap<>();

        if (mappingDocument.triplesMaps == null || mappingDocument.triplesMaps.isEmpty()) {
            return new ResolutionResult(assigned, unmatched, joinIndexes, Collections.emptyList());
        }

        EvaluationContext unsafeCtx = EvaluationContext.createDefault()
                .withBaseIri(fallbackBaseIri)
                .withTemplateSafety(TemplateReferenceSafety.UNSAFE)
                .withValidationMode(LENIENT);

        // 1. Build and index ParentJoinIndex for all ReferencingObjectMaps
        for (TriplesMap tm : mappingDocument.triplesMaps) {
            for (ReferencingObjectMap rom : tm.descendants(ReferencingObjectMap.class).toList()) {
                if (rom.parentTriplesMap != null && rom.parentTriplesMap.logicalSource != null) {
                    AbstractLogicalSource pSource = rom.parentTriplesMap.logicalSource;
                    List<Iteration> pIters = allSourceIterations.get(pSource);
                    if (pIters == null) {
                        EvaluationDataContext pContext = (effectiveDataContext instanceof CustomDataContext || effectiveDataContext instanceof MultiSourceDataContext)
                                ? effectiveDataContext
                                : ((joinIndexLimit != null && joinIndexLimit > 0) ? new RangeDataContext(0, joinIndexLimit) : new AllDataContext());
                        try {
                            pIters = evaluator.resolveIterations(pContext, pSource);
                        } catch (Exception e) {
                            pIters = Collections.emptyList();
                        }
                        allSourceIterations.put(pSource, pIters);
                    }
                    ParentJoinIndex idx = ParentJoinIndex.build(pIters, rom.joinConditions, unsafeCtx);
                    joinIndexes.put(rom, idx);
                }
            }
        }

        // 2. Independent evaluation mode: each TriplesMap independently takes its iteration
        if (mode == InlineHintsEvaluationMode.INDEPENDENT_EVALUATION) {
            for (TriplesMap tm : mappingDocument.triplesMaps) {
                assignDirectIteration(tm, effectiveDataContext, evaluator, assigned);
            }
            List<String> summary = generateTraceSummary(mappingDocument, null, assigned, unmatched, prefixMap);
            return new ResolutionResult(assigned, unmatched, joinIndexes, summary);
        }

        // 3. Trace mode
        TriplesMap anchorTm = findAnchorTriplesMap(mappingDocument, anchorNode, cursorPosition, requestedRange);

        // Assign anchor directly according to effectiveDataContext
        assignDirectIteration(anchorTm, effectiveDataContext, evaluator, assigned);

        AssignedIteration anchorAssigned = assigned.get(anchorTm);
        if (anchorAssigned != null) {
            // Forward joins: anchor is child, resolve referenced parent TriplesMaps
            resolveForwardJoins(anchorTm, anchorAssigned, joinIndexes, unsafeCtx, prefixMap, assigned, unmatched);

            // Reverse joins: anchor is parent, resolve referencing child TriplesMaps
            resolveReverseJoins(anchorTm, anchorAssigned, mappingDocument, effectiveDataContext, evaluator, joinIndexes, unsafeCtx, joinIndexLimit, prefixMap, assigned, unmatched);
        }

        // Transitive forward joins for newly assigned parent TriplesMaps
        boolean changed;
        do {
            changed = false;
            for (TriplesMap tm : new ArrayList<>(assigned.keySet())) {
                AssignedIteration as = assigned.get(tm);
                if (as != null) {
                    int prevAssigned = assigned.size();
                    int prevUnmatched = unmatched.size();
                    resolveForwardJoins(tm, as, joinIndexes, unsafeCtx, prefixMap, assigned, unmatched);
                    if (assigned.size() != prevAssigned || unmatched.size() != prevUnmatched) {
                        changed = true;
                    }
                }
            }
        } while (changed);

        // All TriplesMaps connected to the anchor in the join graph that did not match are unmatched
        LinkedHashSet<TriplesMap> connected = collectConnectedTriplesMaps(anchorTm, mappingDocument);
        for (TriplesMap tm : connected) {
            if (!assigned.containsKey(tm)) {
                unmatched.add(tm);
            }
        }

        // Assign default iterations only to truly independent TriplesMaps outside the join graph
        for (TriplesMap tm : mappingDocument.triplesMaps) {
            if (!connected.contains(tm) && !assigned.containsKey(tm)) {
                assignDirectIteration(tm, effectiveDataContext, evaluator, assigned);
            }
        }

        List<String> summary = generateTraceSummary(mappingDocument, anchorTm, assigned, unmatched, prefixMap);
        return new ResolutionResult(assigned, unmatched, joinIndexes, summary);
    }

    private static void resolveForwardJoins(
            TriplesMap childTm,
            AssignedIteration childAssigned,
            Map<PlanNode, ParentJoinIndex> joinIndexes,
            EvaluationContext unsafeCtx,
            @Nullable Map<String, String> prefixMap,
            Map<TriplesMap, AssignedIteration> assigned,
            Set<TriplesMap> unmatched
    ) {
        String childNodeId = PlanNodeIdRegistry.getId(childTm);
        String childIri = PlanNode.resourceToIriOrBNode(childTm.getIri());
        int cIndex = childAssigned.index();
        Iteration cIter = childAssigned.iteration();

        for (ReferencingObjectMap rom : childTm.descendants(ReferencingObjectMap.class).toList()) {
            TriplesMap parentTm = rom.parentTriplesMap;
            if (parentTm == null || assigned.containsKey(parentTm)) {
                continue;
            }
            ParentJoinIndex idx = joinIndexes.get(rom);
            List<Iteration> pMatches = idx != null
                    ? idx.findMatches(cIter, rom.joinConditions, unsafeCtx)
                    : Collections.emptyList();
            if (!pMatches.isEmpty()) {
                Iteration pIter = pMatches.getFirst();
                int pIndex = pIter.getId() != null ? pIter.getId().index() : 0;
                String joinSummary = formatJoinSummary(rom, prefixMap);
                assigned.put(parentTm, new AssignedIteration(
                        pIter,
                        pIndex,
                        new IterationReason.MatchedJoin(childNodeId, childIri, cIndex, pIndex, joinSummary)
                ));
                unmatched.remove(parentTm);
            } else {
                unmatched.add(parentTm);
            }
        }
    }

    private static void resolveReverseJoins(
            TriplesMap parentTm,
            AssignedIteration parentAssigned,
            MappingDocument mappingDocument,
            EvaluationDataContext effectiveDataContext,
            RmlEvaluator evaluator,
            Map<PlanNode, ParentJoinIndex> joinIndexes,
            EvaluationContext unsafeCtx,
            @Nullable Integer joinIndexLimit,
            @Nullable Map<String, String> prefixMap,
            Map<TriplesMap, AssignedIteration> assigned,
            Set<TriplesMap> unmatched
    ) {
        String parentNodeId = PlanNodeIdRegistry.getId(parentTm);
        String parentIri = PlanNode.resourceToIriOrBNode(parentTm.getIri());
        int pIndex = parentAssigned.index();
        Iteration pIter = parentAssigned.iteration();

        for (TriplesMap childTm : mappingDocument.triplesMaps) {
            if (childTm == parentTm || assigned.containsKey(childTm)) {
                continue;
            }
            List<ReferencingObjectMap> roms = childTm.descendants(ReferencingObjectMap.class).toList();
            for (ReferencingObjectMap rom : roms) {
                if (rom.parentTriplesMap == parentTm) {
                    ParentJoinIndex idx = joinIndexes.get(rom);
                    EvaluationDataContext cContext = (effectiveDataContext instanceof CustomDataContext || effectiveDataContext instanceof MultiSourceDataContext)
                            ? effectiveDataContext
                            : ((joinIndexLimit != null && joinIndexLimit > 0) ? new RangeDataContext(0, joinIndexLimit) : new AllDataContext());
                    List<Iteration> cCandidates;
                    try {
                        cCandidates = evaluator.resolveIterations(cContext, childTm);
                    } catch (Exception e) {
                        cCandidates = Collections.emptyList();
                    }

                    Iteration matchedChild = null;
                    for (Iteration c : cCandidates) {
                        List<Iteration> matches = idx != null
                                ? idx.findMatches(c, rom.joinConditions, unsafeCtx)
                                : Collections.emptyList();
                        boolean matchesTargetParent = matches.stream().anyMatch(m ->
                                (m.getId() != null && pIter.getId() != null && Objects.equals(m.getId(), pIter.getId())) || m.equals(pIter)
                        );
                        if (matchesTargetParent) {
                            matchedChild = c;
                            break;
                        }
                    }

                    if (matchedChild != null) {
                        int cIndex = matchedChild.getId() != null ? matchedChild.getId().index() : 0;
                        String joinSummary = formatJoinSummary(rom, prefixMap);
                        assigned.put(childTm, new AssignedIteration(
                                matchedChild,
                                cIndex,
                                new IterationReason.MatchedJoin(parentNodeId, parentIri, pIndex, cIndex, joinSummary)
                        ));
                        unmatched.remove(childTm);
                    } else {
                        unmatched.add(childTm);
                    }
                    break;
                }
            }
        }
    }

    private static void assignDirectIteration(
            TriplesMap tm,
            EvaluationDataContext dataContext,
            RmlEvaluator evaluator,
            Map<TriplesMap, AssignedIteration> assigned
    ) {
        List<Iteration> iters;
        try {
            iters = evaluator.resolveIterations(dataContext, tm);
        } catch (Exception e) {
            iters = Collections.emptyList();
        }
        if (!iters.isEmpty()) {
            Iteration iter = iters.getFirst();
            int idx = iter.getId() != null ? iter.getId().index() : 0;
            assigned.put(tm, new AssignedIteration(iter, idx, new IterationReason.Direct(idx)));
        }
    }

    private static TriplesMap findAnchorTriplesMap(
            MappingDocument doc,
            @Nullable PlanNode anchorNode,
            @Nullable Position cursorPosition,
            @Nullable Range requestedRange
    ) {
        if (anchorNode != null) {
            PlanNode curr = anchorNode;
            while (curr != null) {
                if (curr instanceof TriplesMap tm) {
                    return tm;
                }
                curr = curr.getParent();
            }
        }
        if (cursorPosition != null) {
            for (TriplesMap tm : doc.triplesMaps) {
                if (matchesCursorOrRange(tm, cursorPosition)) {
                    return tm;
                }
                for (PlanNode node : tm.descendants(PlanNode.class).toList()) {
                    if (matchesCursorOrRange(node, cursorPosition)) {
                        return tm;
                    }
                }
            }
        }
        if (requestedRange != null) {
            for (TriplesMap tm : doc.triplesMaps) {
                Range tmRange = PlanNodeDescriptor.getFirstRangeForNode(tm);
                if (tmRange != null && intersects(tmRange, requestedRange)) {
                    return tm;
                }
                for (PlanNode node : tm.descendants(PlanNode.class).toList()) {
                    Range r = PlanNodeDescriptor.getFirstRangeForNode(node);
                    if (r != null && intersects(r, requestedRange)) {
                        return tm;
                    }
                }
            }
        }
        for (TriplesMap tm : doc.triplesMaps) {
            if (hasReferencingObjectMaps(tm)) {
                return tm;
            }
        }
        return doc.triplesMaps.getFirst();
    }

    private static boolean matchesCursorOrRange(PlanNode node, Position cursorPosition) {
        CursorSelector cursorSel = new CursorSelector("", cursorPosition);
        if (NodeSelectorMatcher.matches(node, cursorSel)) {
            return true;
        }
        Range r = PlanNodeDescriptor.getFirstRangeForNode(node);
        if (r != null && r.getStart() != null && r.getEnd() != null) {
            int line = cursorPosition.getLine();
            int col = cursorPosition.getCharacter();
            boolean afterStart = line > r.getStart().getLine() || (line == r.getStart().getLine() && col >= r.getStart().getCharacter());
            boolean beforeEnd = line < r.getEnd().getLine() || (line == r.getEnd().getLine() && col <= r.getEnd().getCharacter());
            return afterStart && beforeEnd;
        }
        return false;
    }

    private static LinkedHashSet<TriplesMap> collectConnectedTriplesMaps(TriplesMap root, MappingDocument doc) {
        LinkedHashSet<TriplesMap> connected = new LinkedHashSet<>();
        Queue<TriplesMap> queue = new ArrayDeque<>();
        queue.add(root);
        connected.add(root);

        while (!queue.isEmpty()) {
            TriplesMap curr = queue.poll();
            for (ReferencingObjectMap rom : curr.descendants(ReferencingObjectMap.class).toList()) {
                if (rom.parentTriplesMap != null && connected.add(rom.parentTriplesMap)) {
                    queue.add(rom.parentTriplesMap);
                }
            }
            if (doc.triplesMaps != null) {
                for (TriplesMap other : doc.triplesMaps) {
                    if (connected.contains(other)) continue;
                    for (ReferencingObjectMap rom : other.descendants(ReferencingObjectMap.class).toList()) {
                        if (rom.parentTriplesMap == curr && connected.add(other)) {
                            queue.add(other);
                        }
                    }
                }
            }
        }
        return connected;
    }

    public static List<String> generateTraceSummary(
            MappingDocument doc,
            @Nullable TriplesMap anchorTm,
            Map<TriplesMap, AssignedIteration> assigned,
            Set<TriplesMap> unmatched,
            @Nullable Map<String, String> prefixMap
    ) {
        List<String> lines = new ArrayList<>();
        if (doc.triplesMaps == null) {
            return lines;
        }
        List<TriplesMap> orderedMaps = new ArrayList<>();
        if (anchorTm != null) {
            LinkedHashSet<TriplesMap> connected = collectConnectedTriplesMaps(anchorTm, doc);
            orderedMaps.addAll(connected);
            for (TriplesMap tm : doc.triplesMaps) {
                if (!connected.contains(tm)) {
                    orderedMaps.add(tm);
                }
            }
        } else {
            orderedMaps.addAll(doc.triplesMaps);
        }

        for (TriplesMap tm : orderedMaps) {
            String rawIri = PlanNode.resourceToIriOrBNode(tm.getIri());
            String label = prefixMap != null ? RmlEvaluationResultFormatter.compactIri(rawIri, prefixMap) : rawIri;
            if (label == null || label.isBlank()) {
                label = PlanNodeIdRegistry.getId(tm);
            }

            AssignedIteration as = assigned.get(tm);
            if (as != null) {
                if (as.reason() instanceof IterationReason.MatchedJoin mj) {
                    lines.add(label + " Iteration #" + as.index() + " (" + mj.formatSummary(prefixMap) + ")");
                } else {
                    lines.add(label + " Iteration #" + as.index());
                }
            } else if (unmatched.contains(tm)) {
                lines.add(label + ": No join match");
            }
        }
        return lines;
    }

    private static boolean hasReferencingObjectMaps(TriplesMap tm) {
        return !tm.descendants(ReferencingObjectMap.class).toList().isEmpty();
    }

    private static @Nullable String formatJoinSummary(ReferencingObjectMap rom, @Nullable Map<String, String> prefixMap) {
        if (rom.joinConditions.isEmpty()) {
            return null;
        }
        return rom.joinConditions.stream()
                .map(jc -> ExpressionFormatter.formatJoinCondition(jc, prefixMap))
                .collect(Collectors.joining(", "));
    }
}
