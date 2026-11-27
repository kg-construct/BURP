import { TraceNode, TraceValue } from '../../../generated/types';
import { shortenIri } from '../../execution/rdfUtils';
import {TypedRmlEvaluationResult} from "../../common/RdfToken";
import {Util} from "n3";
import isNamedNode = Util.isNamedNode;

export const NODE_WIDTH = 260;
export const NODE_HEADER_BASE_HEIGHT = 28;
export const ATTR_ROW_HEIGHT = 16;
export const NODE_HEADER_HEIGHT = 44;
export const VALUE_ROW_HEIGHT = 26;
export const HORIZONTAL_SPACING = 340;
export const VERTICAL_SPACING = 20;

export interface LayoutNode {
    node: TraceNode;
    id: string;
    rank: number;
    col: number;
    x: number;
    y: number;
    width: number;
    height: number;
}

export interface LayoutEdge {
    sourceId: string;
    targetId: string;
    sourceNode: LayoutNode;
    targetNode: LayoutNode;
}

export interface ColumnNodeState {
    id: string;
    node: TraceNode;
    height: number;
    idealCenter: number;
    y: number;
}

export interface TraceSliceData {
    sliceNodeIds: Set<string>;
    sliceValueIds: Set<string>;
}

export function getNodeHeaderHeight(node: TraceNode): number {
    const attrCount = (node.attributes || []).length;
    return attrCount > 0 ? NODE_HEADER_BASE_HEIGHT + attrCount * ATTR_ROW_HEIGHT : NODE_HEADER_BASE_HEIGHT;
}

export function getNodeHeight(node: TraceNode): number {
    const outCount = Math.max(1, (node.outputs || []).length);
    return getNodeHeaderHeight(node) + outCount * VALUE_ROW_HEIGHT + 10;
}
export function getNodeTypePriority(type: string): number {
    const t = (type || '').toLowerCase();
    if (t.includes('source') || t.includes('logicalview')) {
        return 0;
    }
    if (t.includes('subjectmap')) {
        return 1;
    }
    if (t.includes('constant')) {
        return 2;
    }
    if (t.includes('reference') || t.includes('segment')) {
        return 3;
    }
    if (t.includes('predicatemap') || t.includes('predicate')) {
        return 4;
    }
    if (t.includes('objectmap') || t.includes('object')) {
        return 5;
    }
    if (t.includes('graphmap') || t.includes('graph')) {
        return 6;
    }
    if (t.includes('predicateobject') || t.includes('pom')) {
        return 7;
    }
    if (t.includes('triplesmap')) {
        return 8;
    }
    return 9;
}

export { getNodeTagColor } from '../../common/nodeColors';

export function buildConsumersMap(nodes: Iterable<TraceNode>): Map<string, string[]> {
    const consumersMap = new Map<string, string[]>();
    for (const n of nodes) {
        if (!consumersMap.has(n.nodeId)) {
            consumersMap.set(n.nodeId, []);
        }
    }
    for (const n of nodes) {
        for (const depId of n.dependencyIds || []) {
            let list = consumersMap.get(depId);
            if (!list) {
                list = [];
                consumersMap.set(depId, list);
            }
            list.push(n.nodeId);
        }
    }
    return consumersMap;
}

export function computeTraceSlice(
    hoveredValId: string | null,
    selectedValId: string | null,
    selectedNodeId: string | null,
    valueMap: Map<string, TraceValue>,
    nodeMap: Map<string, TraceNode>,
    consumersMap?: Map<string, string[]>
): TraceSliceData {
    const activeValId = hoveredValId || selectedValId;
    const activeNodeId = selectedNodeId;
    const consumers = consumersMap ?? buildConsumersMap(nodeMap.values());

    const relevantNodes = new Set<string>();
    const relevantValues = new Set<string>();

    if (activeValId && valueMap.has(activeValId)) {
        relevantValues.add(activeValId);
        const val = valueMap.get(activeValId)!;
        if (val.producerNodeId) {
            relevantNodes.add(val.producerNodeId);
        }

        // Backward BFS on derivedFromValueIds
        const queue = [...(val.derivedFromValueIds || [])];
        while (queue.length > 0) {
            const vid = queue.shift()!;
            if (!relevantValues.has(vid)) {
                relevantValues.add(vid);
                const upstream = valueMap.get(vid);
                if (upstream) {
                    if (upstream.producerNodeId) {
                        relevantNodes.add(upstream.producerNodeId);
                    }
                    if (upstream.derivedFromValueIds) {
                        queue.push(...upstream.derivedFromValueIds);
                    }
                }
            }
        }

        // Forward BFS: find all values derived from this value
        const forwardQueue = [activeValId];
        while (forwardQueue.length > 0) {
            const currentId = forwardQueue.shift()!;
            for (const [vid, candidate] of valueMap.entries()) {
                if (candidate.derivedFromValueIds?.includes(currentId) && !relevantValues.has(vid)) {
                    relevantValues.add(vid);
                    if (candidate.producerNodeId) {
                        relevantNodes.add(candidate.producerNodeId);
                    }
                    forwardQueue.push(vid);
                }
            }
        }
    } else if (activeNodeId && nodeMap.has(activeNodeId)) {
        relevantNodes.add(activeNodeId);

        // Upstream BFS
        const upQueue = [...(nodeMap.get(activeNodeId)!.dependencyIds || [])];
        while (upQueue.length > 0) {
            const nid = upQueue.shift()!;
            if (!relevantNodes.has(nid)) {
                relevantNodes.add(nid);
                const n = nodeMap.get(nid);
                if (n?.dependencyIds) {
                    upQueue.push(...n.dependencyIds);
                }
            }
        }

        // Downstream BFS
        const downQueue = [...(consumers.get(activeNodeId) || [])];
        while (downQueue.length > 0) {
            const nid = downQueue.shift()!;
            if (!relevantNodes.has(nid)) {
                relevantNodes.add(nid);
                const nextConsumers = consumers.get(nid);
                if (nextConsumers) {
                    downQueue.push(...nextConsumers);
                }
            }
        }
    }

    return { sliceNodeIds: relevantNodes, sliceValueIds: relevantValues };
}
