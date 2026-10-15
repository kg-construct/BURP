package burp.dev.rml.service.plan;

import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.RmlEvaluationResultFormatter;
import burp.model.*;
import burp.model.lv.LogicalView;
import com.google.common.collect.Sets;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Handles AST inspection, structural navigation, dependencies, iterations, and unused node detection.
 */
public class PlanInspector {

    public CompletableFuture<List<ReferenceDependency>> referencesDependencies(ReferencesDependenciesParams params, MappingWorkspace workspace) {
        try {
            MappingWorkspace.ResolvedNode resolved = workspace.resolveNode(params.structure());
            List<PlanNode> allPlanNodes = new ArrayList<>();
            workspace.collectAllNodes(resolved.mappingDocument(), allPlanNodes);

            Set<String> uniqueReferences = createUniqueReferences(allPlanNodes);

            List<ReferenceDependency> results = new ArrayList<>();
            for (String ref : uniqueReferences) {
                results.add(new ReferenceDependency(
                        "Reference",
                        ref,
                        List.of(ref)
                ));
            }

            return CompletableFuture.completedFuture(results);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static @NonNull Set<String> createUniqueReferences(List<PlanNode> allPlanNodes) {
        Set<String> uniqueReferences = new LinkedHashSet<>();
        for (PlanNode node : allPlanNodes) {
            switch (node) {
                case RawReference rr -> {
                    String ref = rr.getReference();
                    if (ref != null && !ref.isBlank()) {
                        uniqueReferences.add(ref);
                    }
                }
                case Template.ReferenceSegment rs -> {
                    if (rs.reference() != null && rs.reference().getReference() != null && !rs.reference().getReference().isBlank()) {
                        uniqueReferences.add(rs.reference().getReference());
                    }
                }
                case Reference r -> {
                    String ref = r.getReference();
                    if (ref != null && !ref.isBlank()) {
                        uniqueReferences.add(ref);
                    }
                }
                case null, default -> {
                }
            }
        }
        return uniqueReferences;
    }

    public CompletableFuture<PlanResult> plan(PlanParams params, MappingWorkspace workspace) {
        try {
            var mappingDocument = workspace.loadMappingWithStableIds(params.mappingFileUri());
            Map<String, String> prefixMap = workspace.getPrefixMap(params.mappingFileUri());
            String selectedNodeId = null;
            if (params.structure() != null) {
                PlanNode target = workspace.findNodeBySelector(params.structure(), mappingDocument);
                if (target != null) {
                    selectedNodeId = PlanNodeIdRegistry.getId(target);
                }
            }

            List<PlanNodeItem> roots = new ArrayList<>();
            for (TriplesMap tm : mappingDocument.triplesMaps) {
                roots.add(toPlanNodeItem(tm, prefixMap));
            }

            Set<LogicalView> ownedViews = mappingDocument.descendants(LogicalView.class).collect(Collectors.toSet());
            Set<LogicalView> views = mappingDocument.descendantsAndReferences(LogicalView.class).collect(Collectors.toSet());
            views.removeAll(ownedViews);
            for (LogicalView lv : views) {
                roots.add(toPlanNodeItem(lv, prefixMap));
            }

            return CompletableFuture.completedFuture(new PlanResult(roots, selectedNodeId));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }


    private PlanNodeItem toPlanNodeItem(PlanNode node, Map<String, String> prefixMap) {
        String id = PlanNodeIdRegistry.getId(node);
        String type = node.getClass().getSimpleName();
        var range = PlanNodeDescriptor.getRangeForNode(node);
        String resource = null;
        String displayResource = null;
        if (node.getIri() != null) {
            resource = PlanNode.resourceToIriOrBNode(node.getIri());
            String shortForm = RmlEvaluationResultFormatter.compactIri(resource, prefixMap);
            if (shortForm.length() < 12) {
                displayResource = shortForm;
            }
        }
        List<PlanAttributeItem> attributes = new ArrayList<>();
        for (PlanNodeAttribute attr : node.nodeAttributes()) {
            Range attrRange = null;
            if (attr.origin() != null && attr.origin().sourceStatements() != null && !attr.origin().sourceStatements().isEmpty()) {
                attrRange = attr.origin().ranges().stream().sorted().map(burp.reporting.PointRange::toLsp).findFirst().orElse(null);
            }
            attributes.add(new PlanAttributeItem(attr.key(), attr.value(), attrRange));
        }

        List<PlanReferenceItem> references = new ArrayList<>();
        for (PlanNodeReference ref : node.nodeReferences()) {
            PlanNode target = ref.target();
            String targetId = PlanNodeIdRegistry.getId(target);
            String targetResource = PlanNode.resourceToIriOrBNode(target.getIri());
            String targetLabel;
            if (targetResource != null) {
                targetLabel = RmlEvaluationResultFormatter.compactIri(targetResource, prefixMap);
            } else {
                targetLabel = targetId;
            }
            Range refRange = null;
            if (ref.origin() != null && ref.origin().sourceStatements() != null && !ref.origin().sourceStatements().isEmpty()) {
                refRange = ref.origin().ranges().stream().sorted().map(burp.reporting.PointRange::toLsp).findFirst().orElse(null);
            }
            references.add(new PlanReferenceItem(ref.role(), targetId, targetLabel, targetResource, refRange));
        }

        List<PlanNodeItem> children = new ArrayList<>();
        if (node.children() != null) {
            for (PlanNode child : node.children()) {
                children.add(toPlanNodeItem(child, prefixMap));
            }
        }

        return new PlanNodeItem(id, type, range, resource, displayResource, attributes, references, children);
    }
}
