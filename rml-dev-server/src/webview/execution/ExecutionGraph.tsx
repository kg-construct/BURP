import React, { useEffect, useMemo, useState } from "react";
import {
    ExecutionGraphProps,
    GraphNode,
    filterDisplayQuads,
    computeGraphColorMap,
    buildGraphData
} from "./graph/graphModel";
import { useD3Graph } from "./graph/useD3Graph";
import { GraphControlsBar } from "./graph/GraphControlsBar";
import { GraphFloatingControls } from "../common/GraphFloatingControls";
import { GraphNodeDetailsCard } from "./graph/GraphNodeDetailsCard";
import { GraphHullMethod } from "./graph/hullGenerators";

export const ExecutionGraph: React.FC<ExecutionGraphProps> = ({
    quads,
    prefixMap,
    targetOptions,
    selectedTargetId,
    onSelectTarget,
    graphOptions,
    selectedGraphId,
    onSelectGraph,
    defaultHullMode = 'smoothHull'
}) => {
    const [layoutMode, setLayoutMode] = useState<"list" | "organic">("list");
    const hullMode: GraphHullMethod = defaultHullMode || 'smoothHull';
    const [selectedNode, setSelectedNode] = useState<GraphNode | null>(null);
    const [searchQuery, setSearchQuery] = useState<string>("");
    const [activeMatchIndex, setActiveMatchIndex] = useState<number>(0);

    // Filter display quads based on target and graph
    const displayQuads = useMemo(
        () => filterDisplayQuads(quads, selectedTargetId, selectedGraphId),
        [quads, selectedTargetId, selectedGraphId]
    );

    // Graph color palette mapping
    const graphColorMap = useMemo(
        () => computeGraphColorMap(displayQuads),
        [displayQuads]
    );

    // Graph nodes, links, and iteration order
    const { nodes, links, subjectOrder } = useMemo(
        () => buildGraphData(displayQuads, prefixMap),
        [displayQuads, prefixMap]
    );

    // Filter nodes matching search query
    const matchingNodes = useMemo(() => {
        const q = searchQuery.trim().toLowerCase();
        if (!q) return [];
        return nodes.filter((n) => (
            n.fullName.toLowerCase().includes(q) ||
            n.label.toLowerCase().includes(q) ||
            (n.datatype && n.datatype.toLowerCase().includes(q))
        ));
    }, [nodes, searchQuery]);

    // Keep activeMatchIndex in bounds
    useEffect(() => {
        if (matchingNodes.length === 0 || activeMatchIndex >= matchingNodes.length) {
            setActiveMatchIndex(0);
        }
    }, [matchingNodes.length, activeMatchIndex]);

    const activeMatchedNode = matchingNodes[activeMatchIndex] || null;

    // D3 Hook for simulation, zoom, camera, and drag-pinning
    const {
        svgRef,
        containerRef,
        pinnedCount,
        handleZoomIn,
        handleZoomOut,
        handleFitView,
        handleUnpinAll
    } = useD3Graph({
        nodes,
        links,
        subjectOrder,
        graphColorMap,
        layoutMode,
        hullMode,
        prefixMap,
        activeMatchedNode,
        onSelectNode: setSelectedNode
    });

    return (
        <div className="bk-graph-view-wrapper">
            {/* Top Graph Controls Bar */}
            <GraphControlsBar
                layoutMode={layoutMode}
                setLayoutMode={setLayoutMode}
                targetOptions={targetOptions}
                selectedTargetId={selectedTargetId}
                onSelectTarget={onSelectTarget}
                graphOptions={graphOptions}
                selectedGraphId={selectedGraphId}
                onSelectGraph={onSelectGraph}
                searchQuery={searchQuery}
                setSearchQuery={setSearchQuery}
                matchingNodes={matchingNodes}
                activeMatchIndex={activeMatchIndex}
                setActiveMatchIndex={setActiveMatchIndex}
                pinnedCount={pinnedCount}
                onUnpinAll={handleUnpinAll}
                nodeCount={nodes.length}
                linkCount={links.length}
            />

            {/* Edge-to-Edge SVG Canvas */}
            <div className="bk-graph-svg-container" ref={containerRef}>
                <svg ref={svgRef} className="bk-d3-svg" />
            </div>

            {/* Floating Zoom & Fit Controls */}
            <GraphFloatingControls
                onZoomIn={handleZoomIn}
                onZoomOut={handleZoomOut}
                onFitView={handleFitView}
            />

            {/* Selected Node Details Floating Card */}
            {selectedNode && (
                <GraphNodeDetailsCard
                    node={selectedNode}
                    onClose={() => setSelectedNode(null)}
                />
            )}
        </div>
    );
};
