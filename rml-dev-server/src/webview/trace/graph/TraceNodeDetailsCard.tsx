import React from 'react';
import { Tag } from 'baukasten-ui/core';
import { Range, TraceNode } from '../../../generated/types';
import { RdfToken } from '../../common/RdfToken';
import { getNodeTagColor } from './traceGraphModel';

export interface TraceNodeDetailsCardProps {
    node: TraceNode;
    consumers?: string[];
    selectedValId: string | null;
    prefixMap?: Record<string, string>;
    onSelectValId: (valId: string | null) => void;
    onSelectNodeId: (nodeId: string) => void;
    onSelectRange?: (range: Range) => void;
    onClose: () => void;
}

export const TraceNodeDetailsCard: React.FC<TraceNodeDetailsCardProps> = ({
    node,
    consumers,
    selectedValId,
    prefixMap,
    onSelectValId,
    onSelectNodeId,
    onSelectRange,
    onClose
}) => {
    return (
        <div
            style={{
                position: 'absolute',
                top: '16px',
                right: '16px',
                width: '320px',
                maxHeight: 'calc(100% - 32px)',
                backgroundColor: 'var(--vscode-sideBar-background)',
                border: '1px solid var(--vscode-panel-border)',
                borderRadius: '8px',
                boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
                display: 'flex',
                flexDirection: 'column',
                overflow: 'hidden',
                zIndex: 10
            }}
        >
            <div
                style={{
                    padding: '10px 12px',
                    borderBottom: '1px solid var(--vscode-panel-border)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between'
                }}
            >
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <Tag size="xs" color={getNodeTagColor(node.nodeType)}>
                        {node.nodeType}
                    </Tag>
                    <span style={{ fontWeight: 'bold', fontSize: '12px' }}>{node.nodeId}</span>
                    {node.originRange && onSelectRange && (
                        <Tag
                            size="xs"
                            color="blue"
                            style={{ cursor: 'pointer' }}
                            onClick={() => onSelectRange(node.originRange!)}
                            title="Reveal in mapping code"
                        >
                            {`L${node.originRange.start.line + 1}:${node.originRange.start.character + 1}`}
                        </Tag>
                    )}
                </div>
                <button
                    type="button"
                    style={{ background: 'none', border: 'none', color: 'var(--vscode-foreground)', cursor: 'pointer', fontSize: '16px' }}
                    onClick={onClose}
                >
                    &times;
                </button>
            </div>

            <div style={{ padding: '12px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '10px', fontSize: '12px' }}>

                {node.attributes && node.attributes.length > 0 && (
                    <div>
                        <span style={{ color: 'var(--vscode-descriptionForeground)', fontSize: '11px', display: 'block' }}>Attributes:</span>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', marginTop: '4px' }}>
                            {node.attributes.map((attr) => (
                                <div
                                    key={attr.key}
                                    style={{
                                        display: 'flex',
                                        alignItems: 'baseline',
                                        gap: '4px',
                                        fontSize: '11px',
                                        fontFamily: 'var(--vscode-editor-font-family, monospace)',
                                        cursor: attr.originRange && onSelectRange ? 'pointer' : 'default'
                                    }}
                                    onClick={() => attr.originRange && onSelectRange && onSelectRange(attr.originRange)}
                                    title={attr.originRange && onSelectRange ? `${attr.key}: ${attr.value} (click to reveal in mapping)` : `${attr.key}: ${attr.value}`}
                                >
                                    <span style={{ color: 'var(--vscode-descriptionForeground)' }}>{attr.key}:</span>
                                    <span style={{ fontWeight: 500, color: 'var(--vscode-foreground)' }}>{attr.value}</span>
                                </div>
                            ))}
                        </div>
                    </div>
                )}

                <div>
                    <span style={{ color: 'var(--vscode-descriptionForeground)', fontSize: '11px', display: 'block' }}>Produced Values ({node.outputs?.length || 0}):</span>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', marginTop: '4px' }}>
                        {(node.outputs || []).map((v) => (
                            <div
                                key={v.valueId}
                                style={{
                                    padding: '4px 6px',
                                    borderRadius: '4px',
                                    border: selectedValId === v.valueId ? '1px solid var(--vscode-focusBorder)' : '1px solid var(--vscode-panel-border)',
                                    background: 'var(--vscode-editorWidget-background)',
                                    cursor: 'pointer'
                                }}
                                onClick={() => onSelectValId(selectedValId === v.valueId ? null : v.valueId)}
                            >
                                <RdfToken rdf={v.term || { type: 'raw', value: v.text }} prefixMap={prefixMap} />
                                {v.derivedFromValueIds && v.derivedFromValueIds.length > 0 && (
                                    <div style={{ fontSize: '10px', color: 'var(--vscode-descriptionForeground)', marginTop: '2px' }}>
                                        Derived from: {v.derivedFromValueIds.join(', ')}
                                    </div>
                                )}
                            </div>
                        ))}
                    </div>
                </div>

                {node.dependencyIds && node.dependencyIds.length > 0 && (
                    <div>
                        <span style={{ color: 'var(--vscode-descriptionForeground)', fontSize: '11px', display: 'block' }}>Inputs from Nodes:</span>
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px', marginTop: '4px' }}>
                            {node.dependencyIds.map((id) => (
                                <Tag key={id} size="xs" color="gray" onClick={() => onSelectNodeId(id)}>
                                    {id}
                                </Tag>
                            ))}
                        </div>
                    </div>
                )}

                {((consumers && consumers.length > 0) || (node.futureIds && node.futureIds.length > 0)) && (
                    <div>
                        <span style={{ color: 'var(--vscode-descriptionForeground)', fontSize: '11px', display: 'block' }}>Feeds into Nodes:</span>
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px', marginTop: '4px' }}>
                            {(consumers || node.futureIds || []).map((id) => (
                                <Tag key={id} size="xs" color="gray" onClick={() => onSelectNodeId(id)}>
                                    {id}
                                </Tag>
                            ))}
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};
