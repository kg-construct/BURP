import React from 'react';
import { Button, Tag } from 'baukasten-ui/core';
import { ParsedQuadItem } from '../nquadsParser';
import { TermRenderer, GraphRenderer } from '../../common/TermRenderer';
import { SubjectHeader, PredicateHeader, ObjectHeader, GraphHeader, TargetHeader } from './TableHeaders';
import { vscodeApi } from '../../common/vscode';

export interface GroupedQuadTableProps<T> {
    groups: T[];
    getGroupKey: (group: T) => string;
    getGroupQuads: (group: T) => ParsedQuadItem[];
    expandedKeys: Set<string>;
    toggleExpanded: (key: string) => void;
    expandAll: () => void;
    collapseAll: () => void;
    renderGroupHeaderContent: (group: T) => React.ReactNode;
    renderGroupHeaderActions?: (group: T) => React.ReactNode;
    hideSubjectInRows?: boolean;
    prefixMap?: Record<string, string>;
    showTargetColumn?: boolean;
    showGraphColumn?: boolean;
    totalCols: number;
    groupLimit?: number;
    onLoadMore?: () => void;
    isRdfVisualizer?: boolean;
    entityNamePlural: string;
}

export function GroupedQuadTable<T>({
    groups,
    getGroupKey,
    getGroupQuads,
    expandedKeys,
    toggleExpanded,
    expandAll,
    collapseAll,
    renderGroupHeaderContent,
    renderGroupHeaderActions,
    hideSubjectInRows = false,
    prefixMap,
    showGraphColumn = false,
    showTargetColumn = false,
    totalCols,
    groupLimit = 50,
    onLoadMore,
    isRdfVisualizer = false,
    entityNamePlural
}: GroupedQuadTableProps<T>) {
    const displayedGroups = groups.slice(0, groupLimit);

    return (
        <div className="bk-group-table-container">
            <div className="bk-group-toolbar">
                <Button size="xs" variant="secondary" onClick={expandAll}>
                    Expand All
                </Button>
                <Button size="xs" variant="secondary" onClick={collapseAll}>
                    Collapse All
                </Button>
                <span style={{ fontSize: '11px', color: 'var(--vscode-descriptionForeground)', marginLeft: 'auto' }}>
                    Showing {displayedGroups.length} of {groups.length} {entityNamePlural}
                </span>
            </div>
            <div
                className="bk-group-table-scroll"
                onScroll={(e) => {
                    const { scrollHeight, scrollTop, clientHeight } = e.currentTarget;
                    if (scrollHeight - scrollTop - clientHeight < 120 && onLoadMore) {
                        onLoadMore();
                    }
                }}
            >
                <table className="bk-table-custom">
                    <thead>
                        <tr>
                            <th style={{ width: '75px', minWidth: '75px', maxWidth: '85px' }} className="bk-lv-th-index">
                                #
                            </th>
                            <th style={{ width: showGraphColumn && showTargetColumn ? '28%' : showGraphColumn || showTargetColumn ? '32%' : '35%' }}>
                                <SubjectHeader />
                            </th>
                            <th style={{ width: '25%' }}>
                                <PredicateHeader />
                            </th>
                            <th style={{ width: '25%' }}>
                                <ObjectHeader />
                            </th>
                            {showGraphColumn && (
                                <th style={{ width: '12%' }}>
                                    <GraphHeader />
                                </th>
                            )}
                            {showTargetColumn && (
                                <th style={{ width: '12%' }}>
                                    <TargetHeader />
                                </th>
                            )}
                        </tr>
                    </thead>
                    <tbody>
                        {displayedGroups.map((group, groupIdx) => {
                            const key = getGroupKey(group);
                            const isExpanded = expandedKeys.has(key);
                            const quads = getGroupQuads(group);

                            return (
                                <React.Fragment key={key}>
                                    <tr
                                        className="bk-subject-group-row"
                                        onClick={() => toggleExpanded(key)}
                                    >
                                        <td className="bk-lv-td-index-cell" style={{ textAlign: 'center', whiteSpace: 'nowrap' }}>
                                            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '4px' }}>
                                                <span className="bk-lv-td-index">{groupIdx + 1}</span>
                                                {renderGroupHeaderActions && renderGroupHeaderActions(group)}
                                            </div>
                                        </td>
                                        <td colSpan={totalCols - 1}>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                                <span className="bk-expand-btn">{isExpanded ? '▼' : '▶'}</span>
                                                {renderGroupHeaderContent(group)}
                                                <Tag size="xs" color="info">
                                                    {quads.length} {quads.length === 1 ? 'statement' : 'statements'}
                                                </Tag>
                                            </div>
                                        </td>
                                    </tr>
                                    {isExpanded &&
                                        quads.map((q, qIdx) => (
                                            <tr key={q.id || `${key}_${qIdx}`} className="bk-sub-row">
                                                <td className="bk-lv-td-index-cell" style={{ textAlign: 'center', whiteSpace: 'nowrap' }}>
                                                    <div style={{
                                                        display: 'flex',
                                                        alignItems: 'center',
                                                        justifyContent: 'center',
                                                        gap: '4px',
                                                        fontSize: '10px'
                                                    }}>
                                                        <span style={{
                                                            color: 'var(--vscode-descriptionForeground)',
                                                            minWidth: '24px',
                                                            textAlign: 'right'
                                                        }}>
                                                            {qIdx + 1}
                                                        </span>
                                                        {!isRdfVisualizer && (
                                                            <Button
                                                                variant="ghost"
                                                                circular
                                                                size="xs"
                                                                onClick={(e) => {
                                                                    e.stopPropagation();
                                                                    vscodeApi.postMessage({
                                                                        command: 'traceQuad',
                                                                        quad: {
                                                                            subject: q.subject.value,
                                                                            predicate: q.predicate.value,
                                                                            object: q.object.value,
                                                                            graph: q.graphName || '',
                                                                            lineage: q.lineage
                                                                        }
                                                                    });
                                                                }}
                                                                title="Trace this quad"
                                                            >🔍</Button>
                                                        )}
                                                    </div>
                                                </td>
                                                <td>
                                                    {!hideSubjectInRows && (
                                                        <TermRenderer term={q.subject} prefixMap={prefixMap} />
                                                    )}
                                                </td>
                                                <td>
                                                    <TermRenderer term={q.predicate} isPredicate prefixMap={prefixMap} />
                                                </td>
                                                <td>
                                                    <TermRenderer
                                                        term={q.object}
                                                        isObject
                                                        datatype={q.datatypeIri}
                                                        language={q.language}
                                                        prefixMap={prefixMap}
                                                    />
                                                </td>
                                                {showGraphColumn && (
                                                    <td>
                                                        <GraphRenderer graphName={q.graphName} prefixMap={prefixMap} />
                                                    </td>
                                                )}
                                                {showTargetColumn && (
                                                    <td>
                                                        <span className="bk-target-cell-badge">
                                                            {q.targetId === 'default' ? 'Default' : (q.targetId || 'Default')}
                                                        </span>
                                                    </td>
                                                )}
                                            </tr>
                                        ))}
                                </React.Fragment>
                            );
                        })}
                    </tbody>
                </table>
                {groupLimit < groups.length && (
                    <div style={{
                        textAlign: 'center',
                        padding: '12px',
                        fontSize: '12px',
                        color: 'var(--vscode-descriptionForeground)'
                    }}>
                        Loading more {entityNamePlural}...
                    </div>
                )}
            </div>
        </div>
    );
}

