package burp.dev.rml.service.completion;

import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.plan.PlanNodeDescriptor;
import burp.model.*;
import burp.model.fnml.FunctionExecution;
import burp.model.rdf.IRITerm;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/// Contextual payload constructed for an LSP `textDocument/completion` request.
/// Encapsulates the resolved AST node, prefix map, document location, and current editing offsets.
@NullMarked
public record CompletionContext(
        String uri,
        Position position,
        String effectivePrefix,
        @Nullable Range replaceRange,
        MappingWorkspace.LoadedMapping loadedMapping,
        @Nullable PlanNode targetNode,
        Map<String, String> prefixMap,
        MappingWorkspace workspace
) {

    /// Finds the closest ancestor node of the given type, or null.
    public <T extends PlanNode> @Nullable T findAncestor(Class<T> type) {
        return targetNode != null ? targetNode.ancestor(type, true) : null;
    }

    /// Determines whether the target AST node is inside an ancestor of the given type.
    public boolean hasAncestor(Class<? extends PlanNode> type) {
        return findAncestor(type) != null;
    }

    /// Determines whether the target AST node is inside ANY of the given ancestor types.
    @SafeVarargs
    public final boolean hasAnyAncestor(Class<? extends PlanNode>... types) {
        if (targetNode == null) return false;
        for (var type : types) {
            if (targetNode.ancestor(type, true) != null) return true;
        }
        return false;
    }

    /// Retrieves the text on the current line preceding the cursor, up to the last statement or block delimiter (;, [, .).
    public String currentClauseText() {
        String openDoc = workspace.getOpenDocumentText(uri);
        if (openDoc == null) return "";
        String[] lines = openDoc.split("\r?\n");
        int line = position.getLine();
        if (line < 0 || line >= lines.length) return "";
        String lineText = lines[line];
        int charPos = Math.min(position.getCharacter(), lineText.length());
        String beforeOnLine = lineText.substring(0, charPos);
        int lastDelim = Math.max(beforeOnLine.lastIndexOf(';'), Math.max(beforeOnLine.lastIndexOf('['), beforeOnLine.lastIndexOf('.')));
        return (lastDelim >= 0) ? beforeOnLine.substring(lastDelim + 1).trim() : beforeOnLine.trim();
    }

    /// Finds the TriplesMap enclosing the cursor position or target AST node.
    public @Nullable TriplesMap findEnclosingTriplesMap() {
        TriplesMap tm = findAncestor(TriplesMap.class);
        if (tm != null) return tm;

        for (TriplesMap candidate : loadedMapping.document().triplesMaps) {
            Range r = PlanNodeDescriptor.getFirstRangeForNode(candidate);
            if (r != null && isPositionInsideOrAt(r, position)) {
                return candidate;
            }
        }
        if (!loadedMapping.document().triplesMaps.isEmpty()) {
            return loadedMapping.document().triplesMaps.getFirst();
        }
        return null;
    }

    /// Finds the constant predicate IRI associated with the current PredicateObjectMap.
    public @Nullable Resource findAssociatedPredicate() {
        PredicateObjectMap pom = findAncestor(PredicateObjectMap.class);
        if (pom != null) {
            for (PredicateMap pm : pom.predicateMaps) {
                if (pm.expression instanceof RDFNodeConstant c && c.constant instanceof IRITerm iri) {
                    return ResourceFactory.createResource(iri.uri());
                }
            }
        }
        return null;
    }

    /// Finds the FunctionExecution enclosing the cursor or target AST node.
    public @Nullable FunctionExecution findEnclosingFunctionExecution() {
        FunctionExecution fe = findAncestor(FunctionExecution.class);
        if (fe != null) return fe;

        for (TriplesMap t : loadedMapping.document().triplesMaps) {
            for (FunctionExecution candidate : t.descendants(FunctionExecution.class).toList()) {
                if (targetNode != null && candidate.descendants(PlanNode.class).anyMatch(n -> n == targetNode)) {
                    return candidate;
                }
                Range r = PlanNodeDescriptor.getFirstRangeForNode(candidate);
                if (r != null && isPositionInsideOrAt(r, position)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static boolean isPositionInsideOrAt(Range range, Position pos) {
        if (range.getStart() == null || range.getEnd() == null) return false;
        int startLine = range.getStart().getLine();
        int endLine = range.getEnd().getLine();
        int line = pos.getLine();
        if (line < startLine || line > endLine) return false;
        if (line == startLine && pos.getCharacter() < range.getStart().getCharacter()) return false;
        if (line == endLine && pos.getCharacter() > range.getEnd().getCharacter()) return false;
        return true;
    }
}
