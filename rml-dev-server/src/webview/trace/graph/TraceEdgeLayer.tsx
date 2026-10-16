import React from 'react';
import { LayoutEdge } from './traceGraphModel';

export interface TraceEdgeLayerProps {
    edges: LayoutEdge[];
    sliceNodeIds: Set<string>;
}

export const TraceEdgeDefs: React.FC = () => (
    <defs>
        <marker
            id="dag-arrow"
            viewBox="0 0 10 10"
            refX="8"
            refY="5"
            markerWidth="6"
            markerHeight="6"
            orient="auto-start-reverse"
        >
            <path d="M 0 1 L 9 5 L 0 9 z" fill="var(--vscode-editorWidget-border, #666)" />
        </marker>
        <marker
            id="dag-arrow-active"
            viewBox="0 0 10 10"
            refX="8"
            refY="5"
            markerWidth="7"
            markerHeight="7"
            orient="auto-start-reverse"
        >
            <path d="M 0 1 L 9 5 L 0 9 z" fill="var(--bk-subj-color, #38bdf8)" />
        </marker>
    </defs>
);

export const TraceEdgeLayer: React.FC<TraceEdgeLayerProps> = ({ edges, sliceNodeIds }) => {
    return (
        <g className="edges-layer">
            {edges.map((edge) => {
                const sx = edge.sourceNode.x + edge.sourceNode.width;
                const sy = edge.sourceNode.y + edge.sourceNode.height / 2;
                const tx = edge.targetNode.x;
                const ty = edge.targetNode.y + edge.targetNode.height / 2;
                const dx = Math.max(40, Math.abs(tx - sx) * 0.5);

                const isEdgeInSlice =
                    sliceNodeIds.has(edge.sourceId) && sliceNodeIds.has(edge.targetId);

                return (
                    <path
                        key={`${edge.sourceId}->${edge.targetId}`}
                        d={`M ${sx} ${sy} C ${sx + dx} ${sy}, ${tx - dx} ${ty}, ${tx} ${ty}`}
                        fill="none"
                        stroke={isEdgeInSlice ? 'var(--bk-subj-color, #38bdf8)' : 'var(--vscode-editorWidget-border, #454545)'}
                        strokeWidth={isEdgeInSlice ? 2.5 : 1.5}
                        strokeOpacity={sliceNodeIds.size > 0 && !isEdgeInSlice ? 0.25 : 0.8}
                        markerEnd={isEdgeInSlice ? 'url(#dag-arrow-active)' : 'url(#dag-arrow)'}
                    />
                );
            })}
        </g>
    );
};
