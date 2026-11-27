import React from 'react';
import {
    LayoutNode,
    NODE_HEADER_BASE_HEIGHT,
    ATTR_ROW_HEIGHT,
    getNodeHeaderHeight,
    VALUE_ROW_HEIGHT,
} from './traceGraphModel';

export interface TraceNodeCardProps {
    lNode: LayoutNode;
    isSelected: boolean;
    isInSlice: boolean;
    isDimmed: boolean;
    queryMatches: boolean;
    selectedValId: string | null;
    sliceValueIds: Set<string>;
    prefixMap?: Record<string, string>;
    onSelectNode: (nodeId: string) => void;
    onSelectValue: (valId: string | null, nodeId: string) => void;
    onHoverValue: (valId: string | null) => void;
}

export const TraceNodeCard: React.FC<TraceNodeCardProps> = ({
                                                                lNode,
                                                                isSelected,
                                                                isInSlice,
                                                                isDimmed,
                                                                queryMatches,
                                                                selectedValId,
                                                                sliceValueIds,
                                                                onSelectNode,
                                                                onSelectValue,
                                                                onHoverValue
                                                            }) => {
    const headerHeight = getNodeHeaderHeight(lNode.node);
    const attributes = lNode.node.attributes || [];

    return (
        <g
            transform={`translate(${lNode.x}, ${lNode.y})`}
            onClick={() => onSelectNode(lNode.id)}
            style={{cursor: 'pointer', opacity: isDimmed ? 0.35 : 1}}
        >
            {/* Card Background */}
            <rect
                width={lNode.width}
                height={lNode.height}
                rx={8}
                ry={8}
                fill="var(--vscode-sideBar-background, #252526)"
                stroke={
                    isSelected
                        ? 'var(--vscode-focusBorder, #007fd4)'
                        : isInSlice
                            ? 'var(--bk-subj-color, #38bdf8)'
                            : queryMatches
                                ? 'var(--vscode-editorOverviewRuler-findMatchForeground, #ffcc00)'
                                : 'var(--vscode-panel-border, #3c3c3c)'
                }
                strokeWidth={isSelected || isInSlice ? 2 : 1}
            />

            {/* Header Bar */}
            <rect
                width={lNode.width}
                height={headerHeight - 6}
                rx={8}
                ry={8}
                fill="var(--vscode-editorWidget-background, #1e1e1e)"
            />
            <rect
                y={headerHeight - 12}
                width={lNode.width}
                height={6}
                fill="var(--vscode-editorWidget-background, #1e1e1e)"
            />

            {/* Header Text & Type Tag */}
            <text
                x={12}
                y={17}
                fontSize={10}
                fontWeight="600"
                fill="var(--vscode-descriptionForeground, #999)"
            >
                {lNode.node.nodeType}
                {lNode.node.nodeId && (
                    <tspan dx={6} fontSize={10} fontWeight="normal" fill="var(--vscode-foreground, #ccc)">
                        {lNode.node.nodeId}
                    </tspan>
                )}
            </text>

            {/* Attributes List */}
            {attributes.map((attr, aIdx) => {
                const ay = NODE_HEADER_BASE_HEIGHT + aIdx * ATTR_ROW_HEIGHT + 4;
                const attrClipId = `clip-attr-${lNode.id}-${aIdx}`.replace(/[^a-zA-Z0-9_-]/g, '_');
                return (
                    <g key={attr.key}>
                        <clipPath id={attrClipId}>
                            <rect
                                x={12}
                                y={ay - 11}
                                width={Math.max(0, lNode.width - 24)}
                                height={ATTR_ROW_HEIGHT}
                            />
                        </clipPath>
                        <text
                            x={12}
                            y={ay}
                            fontSize={10}
                            fontFamily="var(--vscode-editor-font-family, monospace)"
                            clipPath={`url(#${attrClipId})`}
                        >
                            <title>{`${attr.key}: ${attr.value}`}</title>
                            <tspan fill="var(--vscode-descriptionForeground, #888)">{attr.key}: </tspan>
                            <tspan fill="var(--vscode-foreground, #ccc)" fontWeight="500">{attr.value}</tspan>
                        </text>
                    </g>
                );
            })}

            {/* Outputs List */}
            {(lNode.node.outputs || []).map((val, vIdx) => {
                const vy = headerHeight + vIdx * VALUE_ROW_HEIGHT;
                const isValSelected = selectedValId === val.valueId;
                const isValInSlice = sliceValueIds.has(val.valueId);

                const clipId = `clip-${lNode.id}-${vIdx}`.replace(/[^a-zA-Z0-9_-]/g, '_');

                return (
                    <g
                        key={val.valueId}
                        transform={`translate(6, ${vy})`}
                        onClick={(e) => {
                            e.stopPropagation();
                            onSelectValue(isValSelected ? null : val.valueId, lNode.id);
                        }}
                        onMouseEnter={() => onHoverValue(val.valueId)}
                        onMouseLeave={() => onHoverValue(null)}
                    >
                        <clipPath id={clipId}>
                            <rect
                                x={0}
                                y={0}
                                width={Math.max(0, lNode.width - 20)}
                                height={VALUE_ROW_HEIGHT - 4}
                            />
                        </clipPath>
                        <rect
                            width={lNode.width - 12}
                            height={VALUE_ROW_HEIGHT - 4}
                            rx={4}
                            ry={4}
                            fill={
                                isValSelected
                                    ? 'var(--vscode-editor-selectionBackground, #04395e)'
                                    : isValInSlice
                                        ? 'rgba(56, 189, 248, 0.15)'
                                        : 'var(--vscode-input-background, #3c3c3c)'
                            }
                            stroke={isValInSlice ? 'var(--bk-subj-color, #38bdf8)' : 'transparent'}
                            strokeWidth={1}
                        />
                        <text
                            x={8}
                            y={15}
                            fontSize={11}
                            fontFamily="var(--vscode-editor-font-family)"
                            fill={isValInSlice ? 'var(--bk-subj-color, #38bdf8)' : 'var(--vscode-editor-foreground, #ddd)'}
                            clipPath={`url(#${clipId})`}
                        >
                            <title>{val.text}</title>
                            {val.text}
                        </text>
                    </g>
                );
            })}
        </g>
    );
};
