import React, { useEffect, useMemo, useRef, useState } from 'react';
import * as d3 from 'd3';
import { Range, TraceNode, TraceResult, TraceValue } from '../../generated/types';
import { TraceQuadTarget } from '../protocol/trace';
import { GraphFloatingControls } from '../common/GraphFloatingControls';
import { buildConsumersMap, computeTraceSlice } from './graph/traceGraphModel';
import { computeTopologicalRanks, computeTraceGraphLayout } from './graph/traceLayoutEngine';
import { TraceGraphToolbar } from './graph/TraceGraphToolbar';
import { TraceEdgeDefs, TraceEdgeLayer } from './graph/TraceEdgeLayer';
import { TraceNodeCard } from './graph/TraceNodeCard';
import { TraceNodeDetailsCard } from './graph/TraceNodeDetailsCard';

export interface TraceGraphViewProps {
    result: TraceResult;
    mode: 'iteration' | 'result';
    iterationIndex?: number;
    targetQuad?: TraceQuadTarget;
    title?: string;
    onRefresh?: () => void;
    onSelectRange?: (range: Range) => void;
}

export const TraceGraphView: React.FC<TraceGraphViewProps> = ({
    result,
    mode,
    iterationIndex,
    targetQuad,
    title,
    onRefresh,
    onSelectRange
}) => {
    const svgRef = useRef<SVGSVGElement>(null);
    const containerRef = useRef<HTMLDivElement>(null);
    const gRef = useRef<SVGGElement>(null);
    const zoomRef = useRef<d3.ZoomBehavior<SVGSVGElement, unknown> | null>(null);

    const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
    const [selectedValId, setSelectedValId] = useState<string | null>(null);
    const [hoveredValId, setHoveredValId] = useState<string | null>(null);
    const [searchQuery, setSearchQuery] = useState<string>('');
    const [isolateSlice, setIsolateSlice] = useState<boolean>(false);

    const nodes = result?.nodes || [];
    const prefixMap = result?.prefixMap || {};

    const nodeMap = useMemo(() => {
        return new Map<string, TraceNode>(nodes.map((n) => [n.nodeId, n]));
    }, [nodes]);

    const valueMap = useMemo(() => {
        const map = new Map<string, TraceValue>();
        for (const n of nodes) {
            for (const v of n.outputs || []) {
                map.set(v.valueId, v);
            }
            for (const v of n.inputs || []) {
                map.set(v.valueId, v);
            }
        }
        return map;
    }, [nodes]);

    const consumersMap = useMemo(() => {
        return buildConsumersMap(nodes);
    }, [nodes]);

    const ranks = useMemo(() => {
        return computeTopologicalRanks(nodes, nodeMap, consumersMap);
    }, [nodes, nodeMap, consumersMap]);

    const { sliceNodeIds, sliceValueIds } = useMemo(() => {
        return computeTraceSlice(hoveredValId, selectedValId, selectedNodeId, valueMap, nodeMap, consumersMap);
    }, [hoveredValId, selectedValId, selectedNodeId, valueMap, nodeMap, consumersMap]);

    const displayNodes = useMemo(() => {
        if (!isolateSlice || sliceNodeIds.size === 0) {
            return nodes;
        }
        return nodes.filter((n) => sliceNodeIds.has(n.nodeId));
    }, [nodes, isolateSlice, sliceNodeIds]);

    const { layoutNodes, layoutEdges, bounds } = useMemo(() => {
        return computeTraceGraphLayout(displayNodes, ranks, nodeMap, consumersMap);
    }, [displayNodes, ranks, nodeMap, consumersMap]);

    const handleZoomIn = () => {
        if (!svgRef.current || !zoomRef.current) return;
        d3.select(svgRef.current).transition().duration(250).call(zoomRef.current.scaleBy, 1.25);
    };

    const handleZoomOut = () => {
        if (!svgRef.current || !zoomRef.current) return;
        d3.select(svgRef.current).transition().duration(250).call(zoomRef.current.scaleBy, 0.8);
    };

    const handleFitView = () => {
        if (!svgRef.current || !zoomRef.current || !containerRef.current) return;
        const containerWidth = containerRef.current.clientWidth || 800;
        const containerHeight = containerRef.current.clientHeight || 600;

        if (bounds.width === 0 || bounds.height === 0) return;

        const scale = Math.min(
            (containerWidth - 80) / bounds.width,
            (containerHeight - 80) / bounds.height,
            1.2
        );

        const initialScale = Math.max(scale, 0.25);
        const tx = Math.max(20, (containerWidth - bounds.width * initialScale) / 2);
        const ty = Math.max(20, (containerHeight - bounds.height * initialScale) / 2);

        d3.select(svgRef.current)
            .transition()
            .duration(400)
            .ease(d3.easeCubicOut)
            .call(
                zoomRef.current.transform,
                d3.zoomIdentity.translate(tx, ty).scale(initialScale)
            );
    };

    useEffect(() => {
        if (!svgRef.current || !gRef.current) return;

        const svg = d3.select(svgRef.current);
        const g = d3.select(gRef.current);

        const zoom = d3.zoom<SVGSVGElement, unknown>()
            .scaleExtent([0.15, 3.0])
            .on('zoom', (event) => {
                g.attr('transform', event.transform);
            });

        zoomRef.current = zoom;
        svg.call(zoom);

        handleFitView();
    }, [bounds.width, bounds.height]);

    const handleReset = () => {
        setSelectedNodeId(null);
        setSelectedValId(null);
        setHoveredValId(null);
        setSearchQuery('');
        setIsolateSlice(false);
        handleFitView();
    };

    const activeSelectedNode = selectedNodeId ? nodeMap.get(selectedNodeId) : null;

    return (
        <div className="bk-app-container bk-trace-graph-app" style={{ display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden' }}>
            <TraceGraphToolbar
                mode={mode}
                title={title}
                iterationIndex={iterationIndex}
                targetQuad={targetQuad}
                nodeCount={displayNodes.length}
                searchQuery={searchQuery}
                onSearchChange={setSearchQuery}
                canIsolateSlice={Boolean(selectedNodeId || selectedValId)}
                isolateSlice={isolateSlice}
                onToggleIsolateSlice={() => setIsolateSlice(!isolateSlice)}
                onFitView={handleFitView}
                onReset={handleReset}
                onRefresh={onRefresh}
            />

            <div ref={containerRef} style={{ flex: 1, position: 'relative', overflow: 'hidden', backgroundColor: 'var(--vscode-editor-background)' }}>
                <svg ref={svgRef} style={{ width: '100%', height: '100%', display: 'block', cursor: 'grab' }}>
                    <TraceEdgeDefs />
                    <g ref={gRef}>
                        <TraceEdgeLayer edges={layoutEdges} sliceNodeIds={sliceNodeIds} />
                        <g className="nodes-layer">
                            {layoutNodes.map((lNode) => {
                                const isSelected = selectedNodeId === lNode.id;
                                const isInSlice = sliceNodeIds.has(lNode.id);
                                const isDimmed = sliceNodeIds.size > 0 && !isInSlice;
                                const queryMatches = searchQuery.trim() !== '' && (
                                    lNode.node.nodeId.toLowerCase().includes(searchQuery.toLowerCase()) ||
                                    lNode.node.nodeType.toLowerCase().includes(searchQuery.toLowerCase()) ||
                                    (lNode.node.outputs || []).some((v) => v.text.toLowerCase().includes(searchQuery.toLowerCase()))
                                );

                                return (
                                    <TraceNodeCard
                                        key={lNode.id}
                                        lNode={lNode}
                                        isSelected={isSelected}
                                        isInSlice={isInSlice}
                                        isDimmed={isDimmed}
                                        queryMatches={queryMatches}
                                        selectedValId={selectedValId}
                                        sliceValueIds={sliceValueIds}
                                        prefixMap={prefixMap}
                                        onSelectNode={(nid) => {
                                            setSelectedNodeId((prev) => (prev === nid ? null : nid));
                                            if (lNode.node.originRange && onSelectRange) {
                                                onSelectRange(lNode.node.originRange);
                                            }
                                        }}
                                        onSelectValue={(vid, nid) => {
                                            setSelectedValId(vid);
                                            setSelectedNodeId(nid);
                                        }}
                                        onHoverValue={setHoveredValId}
                                    />
                                );
                            })}
                        </g>
                    </g>
                </svg>

                <GraphFloatingControls
                    onZoomIn={handleZoomIn}
                    onZoomOut={handleZoomOut}
                    onFitView={handleFitView}
                />

                {activeSelectedNode && (
                    <TraceNodeDetailsCard
                        node={activeSelectedNode}
                        consumers={consumersMap.get(activeSelectedNode.nodeId)}
                        selectedValId={selectedValId}
                        prefixMap={prefixMap}
                        onSelectValId={setSelectedValId}
                        onSelectNodeId={(nid) => {
                            setSelectedNodeId(nid);
                            const target = nodeMap.get(nid);
                            if (target?.originRange && onSelectRange) {
                                onSelectRange(target.originRange);
                            }
                        }}
                        onSelectRange={onSelectRange}
                        onClose={() => setSelectedNodeId(null)}
                    />
                )}
            </div>
        </div>
    );
};
