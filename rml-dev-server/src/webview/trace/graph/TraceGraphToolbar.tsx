import React from 'react';
import { Button, Input, Tag } from 'baukasten-ui/core';
import { TraceQuadTarget } from '../../protocol/trace';

export interface TraceGraphToolbarProps {
    mode: 'iteration' | 'result';
    title?: string;
    iterationIndex?: number;
    targetQuad?: TraceQuadTarget;
    nodeCount: number;
    searchQuery: string;
    onSearchChange: (q: string) => void;
    canIsolateSlice: boolean;
    isolateSlice: boolean;
    onToggleIsolateSlice: () => void;
    onFitView: () => void;
    onReset: () => void;
    onRefresh?: () => void;
}

export const TraceGraphToolbar: React.FC<TraceGraphToolbarProps> = ({
    mode,
    title,
    iterationIndex,
    targetQuad,
    nodeCount,
    searchQuery,
    onSearchChange,
    canIsolateSlice,
    isolateSlice,
    onToggleIsolateSlice,
    onFitView,
    onReset,
    onRefresh
}) => {
    return (
        <div className="bk-header-bar" style={{ flexShrink: 0, padding: '8px 12px', borderBottom: '1px solid var(--vscode-panel-border)' }}>
            <div className="bk-header-left" style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                <Tag size="sm" color={mode === 'iteration' ? 'info' : 'purple'}>
                    {mode === 'iteration' ? 'Forward Trace' : 'Backward Provenance Slice'}
                </Tag>
                <span style={{ fontWeight: 'bold', fontSize: '13px' }}>
                    {title || (mode === 'iteration' ? `Iteration #${iterationIndex ?? 0}` : 'Target Quad')}
                </span>
                <Tag size="xs" color="gray">
                    {nodeCount} nodes
                </Tag>
                {targetQuad && (
                    <span
                        style={{ fontSize: '11px', color: 'var(--vscode-descriptionForeground)' }}
                        title={`<${targetQuad.subject} ${targetQuad.predicate} ${targetQuad.object}>`}
                    >
                        Target: {targetQuad.subject.slice(-20)} {targetQuad.predicate.slice(-15)}
                    </span>
                )}
            </div>

            <div className="bk-header-right" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Input
                    size="sm"
                    placeholder="Search nodes or values..."
                    value={searchQuery}
                    onChange={(e) => onSearchChange(e.target.value)}
                    style={{ width: '180px' }}
                />
                {canIsolateSlice && (
                    <Button
                        size="sm"
                        variant={isolateSlice ? 'primary' : 'secondary'}
                        onClick={onToggleIsolateSlice}
                        title="Filter graph to only transitively connected slice nodes"
                    >
                        {isolateSlice ? 'Show Full DAG' : 'Isolate Slice'}
                    </Button>
                )}
                <Button size="sm" variant="secondary" onClick={onFitView} title="Fit Graph into View">
                    Fit
                </Button>
                <Button size="sm" variant="secondary" onClick={onReset} title="Reset selection and view">
                    Reset
                </Button>
                {onRefresh && (
                    <Button size="sm" variant="secondary" onClick={onRefresh} title="Re-run Trace">
                        Refresh
                    </Button>
                )}
            </div>
        </div>
    );
};
