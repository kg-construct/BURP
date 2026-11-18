package burp.dev.rml.service.hint;

import burp.dev.rml.service.plan.PlanNodeDescriptor;
import burp.dev.rml.service.RmlEvaluationResultFormatter;
import burp.ls.FileBasedLogicalSource;
import burp.ls.SourceFile;
import burp.model.*;
import burp.model.lv.LogicalView;
import burp.parse.turtleprov.NodeInfo;
import burp.parse.turtleprov.ProvTurtleVisitor;
import burp.parse.turtleprov.RDF12Converter;
import burp.reporting.PointRange;
import burp.reporting.RDFGraphPointer;
import burp.reporting.RDFPointer;
import org.apache.jena.rdf.model.Statement;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides static LSP Inlay Hints for JoinCondition predicates (rml:child / rml:parent),
 * indicating the underlying source file, logical source, or triples map for each side of the join.
 */
@NullMarked
public class JoinSourceHintProvider {

    public List<InlayHint> extractJoinHints(
            PlanNode rootNode,
            @Nullable Map<String, String> prefixMap
    ) {
        List<InlayHint> hints = new ArrayList<>();
        Map<String, String> safePrefixMap = prefixMap != null ? prefixMap : Collections.emptyMap();
        Set<JoinCondition> joinConditions = rootNode.descendants(JoinCondition.class).collect(Collectors.toSet());
        for (JoinCondition jc : joinConditions) {
            processJoinCondition(jc, hints, safePrefixMap);
        }
        return hints;
    }

    private void processJoinCondition(
            JoinCondition jc,
            List<InlayHint> hints,
            Map<String, String> safePrefixMap
    ) {
        AbstractLogicalSource childLs = jc.childMap != null ? jc.childMap.getLogicalSource() : null;
        AbstractLogicalSource parentLs = jc.parentMap != null ? jc.parentMap.getLogicalSource() : null;

        String childSourceLabel = resolveSourceDisplay(childLs, parentLs, safePrefixMap);
        String parentSourceLabel = resolveSourceDisplay(parentLs, childLs, safePrefixMap);

        addJoinSideHint(jc.childMap, childSourceLabel, "Child", hints);
        addJoinSideHint(jc.parentMap, parentSourceLabel, "Parent", hints);
    }

    private void addJoinSideHint(
            @Nullable ExpressionMap mapNode,
            @Nullable String sourceLabel,
            String sideName,
            List<InlayHint> hints
    ) {
        if (mapNode == null || sourceLabel == null) return;
        Position pos = getPredicatePosition(mapNode);
        if (pos != null) {
            InlayHint hint = new InlayHint();
            hint.setPosition(pos);
            hint.setLabel(Either.forLeft(sourceLabel + " : "));
            hint.setKind(InlayHintKind.Parameter);
            hint.setPaddingRight(true);
            hint.setTooltip(Either.forRight(new MarkupContent("markdown",
                    "**Join Source (" + sideName + ")**: `" + sourceLabel + "`")));
            hints.add(hint);
        }
    }

    public static @Nullable String resolveSourceDisplay(
            @Nullable AbstractLogicalSource ls,
            @Nullable AbstractLogicalSource otherLs,
            Map<String, String> prefixMap
    ) {
        if (ls == null) return null;

        // 1. File check
        String fileName = extractFileName(ls);
        String otherFileName = extractFileName(otherLs);
        if (fileName != null && !fileName.equals(otherFileName)) {
            return fileName;
        }

        // 2. Logical source IRI check
        String lsIri = extractSourceIri(ls, prefixMap);
        if (lsIri != null) {
            return lsIri;
        }

        // 3. Explicit Blank Node Name on Logical Source
        String explicitLsBlank = extractExplicitBlankNodeId(ls);
        if (explicitLsBlank != null) {
            return "_:" + explicitLsBlank;
        }

        return fileName;
    }

    private static @Nullable String extractFileName(@Nullable AbstractLogicalSource ls) {
        if (ls instanceof LogicalView lv && lv.logicalSource != null) {
            return extractFileName(lv.logicalSource);
        }
        if (ls instanceof FileBasedLogicalSource fileSource && fileSource.file != null) {
            if (fileSource.file instanceof SourceFile.Local local) {
                return new File(local.getPath()).getName();
            } else if (fileSource.file instanceof SourceFile.Remote remote) {
                return new File(remote.getUrl()).getName();
            }
        }
        return null;
    }

    private static @Nullable String extractSourceIri(@Nullable AbstractLogicalSource ls, Map<String, String> prefixMap) {
        if (ls == null || ls.getSourcePointers() == null) return null;
        for (RDFPointer ptr : ls.getSourcePointers()) {
            if (ptr instanceof RDFGraphPointer gp && gp.stmt() != null) {
                Statement stmt = gp.stmt();
                if (stmt.getSubject() != null && stmt.getSubject().isURIResource()) {
                    return RmlEvaluationResultFormatter.compactIri(stmt.getSubject().getURI(), prefixMap);
                }
            }
        }
        return null;
    }

    private static @Nullable String extractExplicitBlankNodeId(@Nullable PlanNode node) {
        if (node == null || node.getSourcePointers() == null) return null;
        for (RDFPointer ptr : node.getSourcePointers()) {
            if (ptr instanceof RDFGraphPointer gp && gp.stmt() != null && gp.stmt().getModel() != null) {
                Statement stmt = gp.stmt();
                RDF12Converter.TripleInfo infos = RDF12Converter.fromAnnotations(stmt, stmt.getModel());
                for (NodeInfo ni : infos.toList()) {
                    if (ni != null && ni.blankNodeId() != null && !ni.blankNodeId().isEmpty()) {
                        return ni.blankNodeId();
                    }
                }
            }
        }
        return null;
    }

    public static @Nullable Position getPredicatePosition(@Nullable ExpressionMap node) {
        RDFPointer ptr = null;
        if (node instanceof ParentMap pm) {
            ptr = pm.predicatePointer;
        } else if (node instanceof ChildMap cm) {
            ptr = cm.predicatePointer;
        }
        if (ptr != null) {
            List<PointRange> ranges = ProvTurtleVisitor.retrieveTurtleLocation(List.of(ptr));
            if (!ranges.isEmpty()) {
                var start = ranges.getFirst().start();
                return new Position(start.line(), start.column());
            }
        }
        Range range = PlanNodeDescriptor.getFirstRangeForNode(node);
        return range != null ? range.getStart() : null;
    }
}

