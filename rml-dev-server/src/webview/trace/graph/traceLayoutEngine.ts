import { TraceNode } from '../../../generated/types';
import {
    ColumnNodeState,
    HORIZONTAL_SPACING,
    LayoutEdge,
    LayoutNode,
    NODE_WIDTH,
    VERTICAL_SPACING,
    buildConsumersMap,
    getNodeHeight,
    getNodeTypePriority
} from './traceGraphModel';

export function computeTopologicalRanks(
    nodes: TraceNode[],
    nodeMap: Map<string, TraceNode>,
    consumersMap?: Map<string, string[]>
): Map<string, number> {
    const ranks = new Map<string, number>();
    if (nodes.length === 0) {
        return ranks;
    }

    const nodeSet = new Set(nodes.map((n) => n.nodeId));
    const consumers = consumersMap ?? buildConsumersMap(nodes);

    // 1. Forward pass: compute ASAP (length of longest dependency path from root sources)
    const asap = new Map<string, number>();
    function computeAsap(nodeId: string, visited: Set<string>): number {
        if (asap.has(nodeId)) {
            return asap.get(nodeId)!;
        }
        if (visited.has(nodeId)) {
            return 0;
        }
        visited.add(nodeId);

        const n = nodeMap.get(nodeId);
        if (!n) {
            return 0;
        }

        let maxDepAsap = -1;
        for (const depId of n.dependencyIds || []) {
            if (nodeSet.has(depId)) {
                maxDepAsap = Math.max(maxDepAsap, computeAsap(depId, new Set(visited)));
            }
        }
        const val = maxDepAsap + 1;
        asap.set(nodeId, val);
        return val;
    }

    for (const n of nodes) {
        computeAsap(n.nodeId, new Set());
    }

    const maxAsap = Math.max(0, ...Array.from(asap.values()));

    // 2. Backward pass: compute ALAP (ranking from TriplesMap sinks backwards)
    // Sinks: nodes with no downstream consumers in the active graph
    const alap = new Map<string, number>();

    function computeAlap(nodeId: string, visited: Set<string>): number {
        if (alap.has(nodeId)) {
            return alap.get(nodeId)!;
        }
        if (visited.has(nodeId)) {
            return 0;
        }
        visited.add(nodeId);

        const n = nodeMap.get(nodeId);
        if (!n) {
            return 0;
        }

        const activeConsumers = (consumers.get(nodeId) || []).filter((fid) => nodeSet.has(fid));
        const activeDeps = (n.dependencyIds || []).filter((did) => nodeSet.has(did));

        if (activeConsumers.length === 0) {
            if (activeDeps.length === 0) {
                // Isolated node with no edges
                alap.set(nodeId, 0);
                return 0;
            }
            // Sink node (e.g. TriplesMap) placed in the rightmost column
            alap.set(nodeId, maxAsap);
            return maxAsap;
        }

        let minConsumerAlap = Infinity;
        for (const fid of activeConsumers) {
            minConsumerAlap = Math.min(minConsumerAlap, computeAlap(fid, new Set(visited)));
        }

        const val = minConsumerAlap - 1;
        alap.set(nodeId, val);
        return val;
    }

    for (const n of nodes) {
        computeAlap(n.nodeId, new Set());
    }

    // 3. Normalize so the minimum column index is 0
    let minAlap = Infinity;
    for (const val of alap.values()) {
        if (val < minAlap) {
            minAlap = val;
        }
    }
    const shift = minAlap < Infinity ? minAlap : 0;

    for (const [nid, val] of alap.entries()) {
        ranks.set(nid, val - shift);
    }

    return ranks;
}

export function resolveColumnLayout(
    nodes: ColumnNodeState[],
    minGap: number = 24,
    topMargin: number = 60
): void {
    if (nodes.length === 0) {
        return;
    }

    // 1. Sort nodes in column by idealCenter, tie-break by node type priority
    nodes.sort((a, b) => {
        const diff = a.idealCenter - b.idealCenter;
        if (Math.abs(diff) > 2) {
            return diff;
        }
        const pA = getNodeTypePriority(a.node.nodeType);
        const pB = getNodeTypePriority(b.node.nodeType);
        if (pA !== pB) {
            return pA - pB;
        }
        return a.node.nodeId.localeCompare(b.node.nodeId);
    });

    if (nodes.length === 1) {
        nodes[0].y = Math.max(topMargin, nodes[0].idealCenter - nodes[0].height / 2);
        return;
    }

    // 2. Initial placement based on idealCenter
    for (const n of nodes) {
        n.y = n.idealCenter - n.height / 2;
    }

    // 3. Forward pass: push down overlapping nodes
    for (let i = 1; i < nodes.length; i++) {
        const prev = nodes[i - 1];
        const minAllowedY = prev.y + prev.height + minGap;
        if (nodes[i].y < minAllowedY) {
            nodes[i].y = minAllowedY;
        }
    }

    // 4. Center-shift: calculate average drift from idealCenter
    let totalDrift = 0;
    for (const n of nodes) {
        const actualCenter = n.y + n.height / 2;
        totalDrift += (actualCenter - n.idealCenter);
    }
    const avgDrift = totalDrift / nodes.length;

    // Pull back by average drift to balance the cluster symmetrically
    for (const n of nodes) {
        n.y -= avgDrift;
    }

    // 5. Clamp top margin: ensure no node is higher than topMargin
    let minActualY = Infinity;
    for (const n of nodes) {
        if (n.y < minActualY) {
            minActualY = n.y;
        }
    }
    if (minActualY < topMargin) {
        const shiftDown = topMargin - minActualY;
        for (const n of nodes) {
            n.y += shiftDown;
        }
    }
}

export interface TraceGraphLayoutResult {
    layoutNodes: LayoutNode[];
    layoutEdges: LayoutEdge[];
    bounds: { width: number; height: number };
}

export function computeTraceGraphLayout(
    displayNodes: TraceNode[],
    ranks: Map<string, number>,
    nodeMap: Map<string, TraceNode>,
    consumersMap?: Map<string, string[]>
): TraceGraphLayoutResult {
    const consumers = consumersMap ?? buildConsumersMap(nodeMap.values());

    // Group nodes by raw rank
    const rawRankMap = new Map<number, TraceNode[]>();
    for (const n of displayNodes) {
        const r = ranks.get(n.nodeId) || 0;
        if (!rawRankMap.has(r)) {
            rawRankMap.set(r, []);
        }
        rawRankMap.get(r)!.push(n);
    }

    // Compact active ranks to consecutive column indices
    const sortedRanks = Array.from(rawRankMap.keys()).sort((a, b) => a - b);
    const rankToCol = new Map<number, number>();
    sortedRanks.forEach((r, colIdx) => rankToCol.set(r, colIdx));

    const nodesByCol = new Map<number, TraceNode[]>();
    sortedRanks.forEach((r) => {
        const col = rankToCol.get(r)!;
        nodesByCol.set(col, rawRankMap.get(r)!);
    });

    const totalCols = sortedRanks.length;
    const currentCenter = new Map<string, number>();
    const currentY = new Map<string, number>();

    // 1. Initial seed placement:
    // Place rightmost column (sinks / TriplesMaps) stacked with ample spacing
    const lastColNodes = nodesByCol.get(totalCols - 1) || [];
    let initY = 60;
    for (const n of lastColNodes) {
        const h = getNodeHeight(n);
        currentY.set(n.nodeId, initY);
        currentCenter.set(n.nodeId, initY + h / 2);
        initY += h + 80;
    }

    // Initialize intermediate columns around average sink height
    const avgSinkCenter = lastColNodes.length > 0
        ? (Array.from(currentCenter.values()).reduce((a, b) => a + b, 0) / lastColNodes.length)
        : 200;

    for (let col = 0; col < totalCols - 1; col++) {
        const cNodes = nodesByCol.get(col) || [];
        let cY = 60;
        for (const n of cNodes) {
            const h = getNodeHeight(n);
            currentY.set(n.nodeId, cY);
            currentCenter.set(n.nodeId, avgSinkCenter);
            cY += h + VERTICAL_SPACING;
        }
    }

    // Helper to solve layout for one column
    const solveCol = (col: number, getIdeal: (n: TraceNode) => number) => {
        const colNodes = nodesByCol.get(col) || [];
        if (colNodes.length === 0) {
            return;
        }

        const stateList: ColumnNodeState[] = colNodes.map((n) => ({
            id: n.nodeId,
            node: n,
            height: getNodeHeight(n),
            idealCenter: getIdeal(n),
            y: currentY.get(n.nodeId) ?? 60
        }));

        resolveColumnLayout(stateList, VERTICAL_SPACING, 60);

        for (const s of stateList) {
            currentY.set(s.id, s.y);
            currentCenter.set(s.id, s.y + s.height / 2);
        }
    };

    // Sweep 1: Backward pass (Right -> Left)
    for (let col = totalCols - 2; col >= 0; col--) {
        solveCol(col, (n) => {
            const consumerNodes = (consumers.get(n.nodeId) || [])
                .map((fid) => nodeMap.get(fid))
                .filter((fn): fn is TraceNode => fn !== undefined && currentCenter.has(fn.nodeId));

            if (consumerNodes.length > 0) {
                return consumerNodes.reduce((sum, c) => sum + currentCenter.get(c.nodeId)!, 0) / consumerNodes.length;
            }
            return currentCenter.get(n.nodeId) ?? avgSinkCenter;
        });
    }

    // Sweep 2: Forward pass (Left -> Right)
    for (let col = 1; col < totalCols; col++) {
        solveCol(col, (n) => {
            const deps = (n.dependencyIds || [])
                .map((did) => nodeMap.get(did))
                .filter((dn): dn is TraceNode => dn !== undefined && currentCenter.has(dn.nodeId));

            const consumerNodes = (consumers.get(n.nodeId) || [])
                .map((fid) => nodeMap.get(fid))
                .filter((fn): fn is TraceNode => fn !== undefined && currentCenter.has(fn.nodeId));

            if (deps.length > 0 && consumerNodes.length > 0) {
                const avgDeps = deps.reduce((sum, d) => sum + currentCenter.get(d.nodeId)!, 0) / deps.length;
                const avgCons = consumerNodes.reduce((sum, c) => sum + currentCenter.get(c.nodeId)!, 0) / consumerNodes.length;
                return 0.6 * avgDeps + 0.4 * avgCons;
            } else if (deps.length > 0) {
                return deps.reduce((sum, d) => sum + currentCenter.get(d.nodeId)!, 0) / deps.length;
            } else if (consumerNodes.length > 0) {
                return consumerNodes.reduce((sum, c) => sum + currentCenter.get(c.nodeId)!, 0) / consumerNodes.length;
            }
            return currentCenter.get(n.nodeId) ?? avgSinkCenter;
        });
    }

    // Sweep 3: Backward pass (Right -> Left final horizon locking)
    for (let col = totalCols - 2; col >= 0; col--) {
        solveCol(col, (n) => {
            const consumerNodes = (consumers.get(n.nodeId) || [])
                .map((fid) => nodeMap.get(fid))
                .filter((fn): fn is TraceNode => fn !== undefined && currentCenter.has(fn.nodeId));

            const deps = (n.dependencyIds || [])
                .map((did) => nodeMap.get(did))
                .filter((dn): dn is TraceNode => dn !== undefined && currentCenter.has(dn.nodeId));

            if (consumerNodes.length > 0) {
                const avgCons = consumerNodes.reduce((sum, c) => sum + currentCenter.get(c.nodeId)!, 0) / consumerNodes.length;
                if (deps.length > 0) {
                    const avgDeps = deps.reduce((sum, d) => sum + currentCenter.get(d.nodeId)!, 0) / deps.length;
                    return 0.8 * avgCons + 0.2 * avgDeps;
                }
                return avgCons;
            } else if (deps.length > 0) {
                return deps.reduce((sum, d) => sum + currentCenter.get(d.nodeId)!, 0) / deps.length;
            }
            return currentCenter.get(n.nodeId) ?? avgSinkCenter;
        });
    }

    // Global normalization: shift graph up if there's excessive top padding
    let minGraphY = Infinity;
    for (let col = 0; col < totalCols; col++) {
        const colNodes = nodesByCol.get(col) || [];
        for (const n of colNodes) {
            const y = currentY.get(n.nodeId);
            if (y !== undefined && y < minGraphY) {
                minGraphY = y;
            }
        }
    }
    if (minGraphY > 60 && minGraphY < Infinity) {
        const lift = minGraphY - 60;
        for (const [nid, y] of currentY.entries()) {
            currentY.set(nid, y - lift);
            currentCenter.set(nid, currentCenter.get(nid)! - lift);
        }
    }

    // Build layout nodes
    const lNodes: LayoutNode[] = [];
    const lNodeMap = new Map<string, LayoutNode>();
    let maxX = 0;
    let maxY = 0;

    for (let col = 0; col < totalCols; col++) {
        const colNodes = nodesByCol.get(col) || [];
        const x = 60 + col * HORIZONTAL_SPACING;

        for (const n of colNodes) {
            const placedY = currentY.get(n.nodeId) ?? 60;
            const h = getNodeHeight(n);

            const lNode: LayoutNode = {
                node: n,
                id: n.nodeId,
                rank: ranks.get(n.nodeId) || 0,
                col,
                x,
                y: placedY,
                width: NODE_WIDTH,
                height: h
            };

            lNodes.push(lNode);
            lNodeMap.set(n.nodeId, lNode);

            if (placedY + h > maxY) {
                maxY = placedY + h;
            }
        }

        if (x + NODE_WIDTH > maxX) {
            maxX = x + NODE_WIDTH;
        }
    }

    // Edges
    const lEdges: LayoutEdge[] = [];
    for (const targetNode of lNodes) {
        for (const depId of targetNode.node.dependencyIds || []) {
            const sourceNode = lNodeMap.get(depId);
            if (sourceNode) {
                lEdges.push({
                    sourceId: depId,
                    targetId: targetNode.id,
                    sourceNode,
                    targetNode
                });
            }
        }
    }

    return {
        layoutNodes: lNodes,
        layoutEdges: lEdges,
        bounds: { width: maxX + 100, height: maxY + 100 }
    };
}
