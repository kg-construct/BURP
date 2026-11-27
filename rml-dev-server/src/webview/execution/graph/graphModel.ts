import * as d3 from 'd3';
import { ParsedQuadItem } from '../nquadsParser';
import { shortenIri } from '../rdfUtils';
import { PartitionOption } from '../../common/PartitionSelector';
import { GraphHullMethod } from './hullGenerators';

export interface ExecutionGraphProps {
    quads: ParsedQuadItem[];
    prefixMap?: Record<string, string>;
    targetOptions?: PartitionOption[];
    selectedTargetId?: string;
    onSelectTarget?: (targetId: string) => void;
    graphOptions?: PartitionOption[];
    selectedGraphId?: string;
    onSelectGraph?: (graphId: string) => void;
    defaultHullMode?: GraphHullMethod;
}

export interface GraphNode extends d3.SimulationNodeDatum {
    id: string;
    label: string;
    fullName: string;
    termType: 'NamedNode' | 'BlankNode' | 'Literal';
    datatype?: string;
    language?: string;
    graphName: string;
    primarySubject: string;
    iterationKey?: string;
    iterationIndex: number;
    isPinned?: boolean;
    targetY?: number;
}

export interface GraphLink extends d3.SimulationLinkDatum<GraphNode> {
    id: string;
    source: string | GraphNode;
    target: string | GraphNode;
    predicate: string;
    predicateLabel: string;
    graphName: string;
    linkIndex: number;
    linkTotal: number;
    targetDistance: number;
    springStrength: number;
}

export const GRAPH_PALETTE = [
    '#38bdf8', // sky blue
    '#a78bfa', // purple
    '#f472b6', // pink
    '#fb923c', // orange
    '#34d399', // emerald
    '#facc15', // yellow
    '#60a5fa', // blue
    '#64748b'  // slate for default
];

export function filterDisplayQuads(
    quads: ParsedQuadItem[],
    selectedTargetId?: string,
    selectedGraphId?: string
): ParsedQuadItem[] {
    let list = quads;
    if (selectedTargetId && selectedTargetId !== 'all') {
        list = list.filter((q) => q.targetId === selectedTargetId);
    }
    if (selectedGraphId && selectedGraphId !== 'all') {
        list = list.filter((q) => q.graphName === selectedGraphId);
    }
    return list;
}

export function computeGraphColorMap(displayQuads: ParsedQuadItem[]): Map<string, string> {
    const uniqueGraphs = Array.from(new Set(displayQuads.map((q) => q.graphName || '(default)'))).sort();
    const map = new Map<string, string>();
    let colorIdx = 0;
    for (const g of uniqueGraphs) {
        if (g === '(default)') {
            map.set(g, '#64748b');
        } else {
            map.set(g, GRAPH_PALETTE[colorIdx % (GRAPH_PALETTE.length - 1)]);
            colorIdx++;
        }
    }
    return map;
}

export function buildGraphData(
    displayQuads: ParsedQuadItem[],
    prefixMap?: Record<string, string>
): {
    nodes: GraphNode[];
    links: GraphLink[];
    subjectOrder: string[];
} {
    const nodeMap = new Map<string, GraphNode>();
    const rawLinks: Array<{
        id: string;
        source: string;
        target: string;
        predicate: string;
        predicateLabel: string;
        graphName: string;
    }> = [];

    const iterationOrder: string[] = [];
    const iterationIndexMap = new Map<string, number>();

    for (const q of displayQuads) {
        const iterKey = q.lineage?.primary
            ? `${q.lineage.primary.logicalSourceId}#${q.lineage.primary.index}`
            : q.subject.value;
        if (!iterationIndexMap.has(iterKey)) {
            iterationIndexMap.set(iterKey, iterationOrder.length);
            iterationOrder.push(iterKey);
        }
    }

    for (const q of displayQuads) {
        const sVal = q.subject.value;
        const iterKey = q.lineage?.primary
            ? `${q.lineage.primary.logicalSourceId}#${q.lineage.primary.index}`
            : sVal;
        const iterIdx = iterationIndexMap.get(iterKey) ?? 0;
        const gName = q.graphName || '(default)';

        // Subject node
        const sId = q.subject.termType === 'NamedNode'
            ? `iri:${sVal}`
            : `bnode:${sVal}`;

        if (!nodeMap.has(sId)) {
            nodeMap.set(sId, {
                id: sId,
                label: q.subject.termType === 'NamedNode'
                    ? shortenIri(sVal, prefixMap).display
                    : `_:${sVal}`,
                fullName: sVal,
                termType: q.subject.termType as any,
                graphName: gName,
                primarySubject: sVal,
                iterationKey: iterKey,
                iterationIndex: iterIdx
            });
        }

        // Object node
        let oId = '';
        let oLabel = '';
        if (q.object.termType === 'NamedNode') {
            oId = `iri:${q.object.value}`;
            oLabel = shortenIri(q.object.value, prefixMap).display;
        } else if (q.object.termType === 'BlankNode') {
            oId = `bnode:${q.object.value}`;
            oLabel = `_:${q.object.value}`;
        } else {
            oId = `lit:${q.object.value}_${q.datatypeIri || ''}_${q.language || ''}`;
            oLabel = q.object.value.length > 22
                ? `"${q.object.value.substring(0, 19)}..."`
                : `"${q.object.value}"`;
        }

        if (!nodeMap.has(oId)) {
            nodeMap.set(oId, {
                id: oId,
                label: oLabel,
                fullName: q.object.value,
                termType: q.object.termType as any,
                datatype: q.datatypeIri,
                language: q.language,
                graphName: gName,
                primarySubject: sVal,
                iterationKey: iterKey,
                iterationIndex: iterIdx
            });
        }

        // Link
        const pLabel = shortenIri(q.predicate.value, prefixMap).display;
        rawLinks.push({
            id: `${sId}->${q.predicate.value}->${oId}`,
            source: sId,
            target: oId,
            predicate: q.predicate.value,
            predicateLabel: pLabel,
            graphName: gName
        });
    }

    // Multi-edge indexing for curves and normalized physics
    const pairGroups = new Map<string, number[]>();
    rawLinks.forEach((l, idx) => {
        const pairKey = l.source < l.target ? `${l.source}~~${l.target}` : `${l.target}~~${l.source}`;
        const group = pairGroups.get(pairKey) || [];
        group.push(idx);
        pairGroups.set(pairKey, group);
    });

    const linkList: GraphLink[] = rawLinks.map((l, idx) => {
        const pairKey = l.source < l.target ? `${l.source}~~${l.target}` : `${l.target}~~${l.source}`;
        const group = pairGroups.get(pairKey)!;
        const linkIndex = group.indexOf(idx);
        const linkTotal = group.length;

        // Dynamic distance guaranteeing label readability:
        // Math.max(95, label.length * 8 + 35) + extra curve allowance if multiple links
        const targetDistance = Math.max(95, l.predicateLabel.length * 8 + 35) + (linkTotal > 1 ? 25 : 0);

        // Normalize spring pull across multiple links between the same nodes
        const springStrength = 0.6 / linkTotal;

        return {
            ...l,
            linkIndex,
            linkTotal,
            targetDistance,
            springStrength
        };
    });

    return {
        nodes: Array.from(nodeMap.values()),
        links: linkList,
        subjectOrder: iterationOrder
    };
}
