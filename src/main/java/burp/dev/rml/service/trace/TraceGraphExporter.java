package burp.dev.rml.service.trace;

import burp.dev.rml.service.plan.PlanNodeDescriptor;
import burp.dev.rml.service.plan.PlanAttributeItem;
import burp.model.*;
import burp.reporting.PointRange;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;

/// Converts an in-memory [`TraceGraph`] into external LSP [`TraceResult`] DTOs
/// and PROV-O/RER JSON-LD serialization.
///
/// ### Serialization Phases
///
/// 1. **Phase 1 (ID Assignment)**: Generates deterministic, iteration-scoped value IDs
///    (`val-<iterationIndex>-<nodeId>-<valueIndex>`) for all input and output values.
/// 2. **Phase 2 (DTO Instantiation)**: Translates [`TraceGraph.Value`] into immutable [`TraceValue`]
///    DTOs, resolving `derivedFrom` object references into string IDs.
/// 3. **Phase 3 (Node Assembly & PROV Serialization)**: Builds [`TraceNode`] DTOs and serializes
///    the graph to W3C PROV-O / RER JSON-LD using [`RerProvSerializer`].
@NullMarked
public final class TraceGraphExporter {

    private TraceGraphExporter() {}

    public static TraceResult export(TraceGraph graph, Map<String, String> prefixMap) {
        Map<TraceGraph.Value, String> valueIdMap = new IdentityHashMap<>();
        Map<String, TraceValue> allTraceValues = new LinkedHashMap<>();

        // First pass: assign canonical value IDs to all values
        for (TraceGraph.Node node : graph.getNodes()) {
            String nodeId = PlanNodeIdRegistry.getId(node.planNode());

            assignValueIds(node.inputs(), nodeId, valueIdMap);
            assignValueIds(node.outputs(), nodeId, valueIdMap);
        }

        // Second pass: instantiate DTO TraceValues with resolved derivedFromValueIds
        for (TraceGraph.Node node : graph.getNodes()) {
            String nodeId = PlanNodeIdRegistry.getId(node.planNode());

            for (TraceGraph.Value val : node.inputs()) {
                getOrCreateDtoValue(val, nodeId, valueIdMap, allTraceValues);
            }
            for (TraceGraph.Value val : node.outputs()) {
                getOrCreateDtoValue(val, nodeId, valueIdMap, allTraceValues);
            }
        }

        // Third pass: build TraceNode DTOs
        List<TraceNode> traceNodes = new ArrayList<>(graph.getNodes().size());
        for (TraceGraph.Node node : graph.getNodes()) {
            String nodeId = PlanNodeIdRegistry.getId(node.planNode());
            PlanNode pn = node.planNode();

            List<TraceValue> dtoInputs = node.inputs().stream()
                    .map(v -> allTraceValues.get(valueIdMap.get(v)))
                    .filter(Objects::nonNull)
                    .toList();

            List<TraceValue> dtoOutputs = node.outputs().stream()
                    .map(v -> allTraceValues.get(valueIdMap.get(v)))
                    .filter(Objects::nonNull)
                    .toList();

            List<String> depIds = node.upstream().stream()
                    .map(u -> PlanNodeIdRegistry.getId(u.planNode()))
                    .toList();

            List<String> futureIds = node.downstream().stream()
                    .map(d -> PlanNodeIdRegistry.getId(d.planNode()))
                    .toList();

            List<PlanAttributeItem> dtoAttributes = new ArrayList<>();
            for (PlanNodeAttribute attr : pn.nodeAttributes()) {
                Range attrRange = null;
                if (attr.origin() != null && attr.origin().sourceStatements() != null && !attr.origin().sourceStatements().isEmpty()) {
                    attrRange = attr.origin().ranges().stream().sorted().map(PointRange::toLsp).findFirst().orElse(null);
                }
                dtoAttributes.add(new PlanAttributeItem(attr.key(), attr.value(), attrRange));
            }

            traceNodes.add(new TraceNode(
                    nodeId,
                    pn.getNodeType(),
                    computeLabel(pn),
                    PlanNodeDescriptor.getFirstRangeForNode(pn),
                    dtoAttributes,
                    dtoInputs,
                    dtoOutputs,
                    depIds,
                    futureIds
            ));
        }

        String graphJson = RerProvSerializer.serialize(traceNodes, allTraceValues);
        return new TraceResult(traceNodes, graphJson, prefixMap);
    }

    private static void assignValueIds(List<TraceGraph.Value> values, String nodeId, Map<TraceGraph.Value, String> valueIdMap) {
        for (int k = 0; k < values.size(); k++) {
            TraceGraph.Value val = values.get(k);
            if (!valueIdMap.containsKey(val)) {
                String iterId = resolveIterationId(val.iteration());
                String valId = "val-" + iterId + "-" + nodeId + "-" + k;
                valueIdMap.put(val, valId);
            }
        }
    }

    private static TraceValue getOrCreateDtoValue(
            TraceGraph.Value val,
            String nodeId,
            Map<TraceGraph.Value, String> valueIdMap,
            Map<String, TraceValue> allTraceValues
    ) {
        String valId = Objects.requireNonNull(valueIdMap.get(val));
        TraceValue existing = allTraceValues.get(valId);
        if (existing != null) {
            return existing;
        }

        String iterId = resolveIterationId(val.iteration());
        List<String> derivedFromIds = val.derivedFrom().stream()
                .map(valueIdMap::get)
                .filter(Objects::nonNull)
                .toList();

        TraceValue dto = new TraceValue(
                valId,
                iterId,
                val.text(),
                val.result(),
                PlanNodeIdRegistry.getId(val.producer()),
                derivedFromIds
        );
        allTraceValues.put(valId, dto);
        return dto;
    }

    private static String resolveIterationId(@Nullable Iteration iter) {
        if (iter != null && iter.getId() != null) {
            return "iter-" + iter.getId().index();
        }
        return "iter-0";
    }

    private static String computeLabel(PlanNode pn) {
        String label = null;
        if (pn instanceof ExpressionMap em) {
            if (em.expression instanceof Template t) label = t.getTemplate();
            else if (em.expression instanceof RawReference r) label = r.getReference();
            else if (em.expression instanceof RDFNodeConstant c) label = c.constant.toString();
        } else if (pn instanceof Reference ref) {
            label = ref.getReference();
        } else if (pn.getIri() != null) {
            label = PlanNode.resourceToIriOrBNode(pn.getIri());
        }
        return label != null ? label : pn.getNodeType();
    }
}
