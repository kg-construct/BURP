import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Tag } from 'baukasten-ui/core';
import { useReactTable, getCoreRowModel, flexRender } from '@tanstack/react-table';
import { ParsedQuadItem } from './nquadsParser';
import { TableFilterContext } from './table/TableFilterContext';
import { useTableData } from './table/useTableData';
import { GroupBySubjectTable } from './table/GroupBySubjectTable';
import { GroupByIterationTable } from './table/GroupByIterationTable';
import {SegmentedButton} from "../common/SegmentedButton";
import {webviewTelemetry} from "../common/telemetry";

export interface ExecutionTableProps {
    quads: ParsedQuadItem[];
    prefixMap?: Record<string, string>;
    logicalSources?: Record<string, string>;
    showGraphColumn?: boolean;
    showTargetColumn?: boolean;
    isRdfVisualizer?: boolean;
}

/**
 * ExecutionTable renders the execution results.
 * It can display the RDF produced by an RML mapping execution,
 * or arbitrary RDF content when `isRdfVisualizer` is true.
 */
export const ExecutionTable: React.FC<ExecutionTableProps> = ({
    quads,
    prefixMap,
    logicalSources,
    showGraphColumn = false,
    showTargetColumn = false,
    isRdfVisualizer = false,
}) => {


    const {
        grouping,
        setGrouping,
        expandedSubjects,
        setExpandedSubjects,
        expandedIterations,
        setExpandedIterations,
        sorting,
        filteredQuads,
        sortedFilteredQuads,
        groupedBySubject,
        groupedByIteration,
        toggleSubjectExpanded,
        expandAllSubjects,
        collapseAllSubjects,
        toggleIterationExpanded,
        expandAllIterations,
        collapseAllIterations,
        filterContextValue,
        columns,
        totalCols
    } = useTableData({
        quads,
        prefixMap,
        logicalSources,
        showGraphColumn,
        showTargetColumn,
        isRdfVisualizer
    });

    const [flatLimit, setFlatLimit] = useState<number>(50);
    const [groupLimit, setGroupLimit] = useState<number>(50);

    useEffect(() => {
        setFlatLimit(50);
        setGroupLimit(50);
    }, [filteredQuads, sorting, grouping]);

    const slicedFlatQuads = useMemo(() => {
        return sortedFilteredQuads.slice(0, flatLimit);
    }, [sortedFilteredQuads, flatLimit]);

    const table = useReactTable({
        data: slicedFlatQuads,
        columns,
        getCoreRowModel: getCoreRowModel()
    });

    const handleFlatScroll = (e: React.UIEvent<HTMLDivElement>) => {
        const { scrollHeight, scrollTop, clientHeight } = e.currentTarget;
        if (scrollHeight - scrollTop - clientHeight < 120) {
            if (flatLimit < sortedFilteredQuads.length) {
                const nextLimit = Math.min(flatLimit + 50, sortedFilteredQuads.length);
                setFlatLimit(nextLimit);
                webviewTelemetry.capture('execution_scroll_loaded_more', {
                    view_type: 'flat',
                    current_limit: nextLimit,
                    reached_end: nextLimit >= sortedFilteredQuads.length
                });
            }
        }
    };

    const handleLoadMoreGroups = () => {
        if (grouping === 'subject' && groupedBySubject && groupLimit < groupedBySubject.length) {
            const nextLimit = Math.min(groupLimit + 50, groupedBySubject.length);
            setGroupLimit(nextLimit);
            webviewTelemetry.capture('execution_scroll_loaded_more', {
                view_type: 'grouped',
                current_limit: nextLimit,
                reached_end: nextLimit >= groupedBySubject.length
            });
        } else if (grouping === 'iteration' && groupedByIteration && groupLimit < groupedByIteration.length) {
            const nextLimit = Math.min(groupLimit + 50, groupedByIteration.length);
            setGroupLimit(nextLimit);
            webviewTelemetry.capture('execution_scroll_loaded_more', {
                view_type: 'grouped',
                current_limit: nextLimit,
                reached_end: nextLimit >= groupedByIteration.length
            });
        }
    };

    const totalGroupCount = grouping === 'iteration'
        ? (groupedByIteration?.length || 0)
        : (groupedBySubject?.length || 0);

    const groupUnitName = grouping === 'flat' ? 'quads' : grouping === 'iteration' ? 'iterations' : 'subjects';

    return (
        <TableFilterContext.Provider value={filterContextValue}>
            <div className="bk-execution-table-container">
                {/* Toolbar */}
                <div className="bk-exec-toolbar">
                    <div className="bk-exec-toolbar-row">
                        <SegmentedButton
                          options={
                            isRdfVisualizer
                              ? [
                                  { id: 'flat', label: 'Flat Table', active: grouping === 'flat' },
                                  { id: 'subject', label: 'Group by Subject', active: grouping === 'subject' }
                                ]
                              : [
                                  { id: 'flat', label: 'Flat Table', active: grouping === 'flat' },
                                  { id: 'subject', label: 'Group by Subject', active: grouping === 'subject' },
                                  { id: 'iteration', label: 'Group by Iteration', active: grouping === 'iteration' }
                                ]
                          }
                          onSelect={(id) => {
                            webviewTelemetry.capture('execution_group_by_changed', { group_by: id });
                            if (id === 'flat') {
                              setGrouping('flat');
                            } else if (id === 'subject') {
                              setGrouping('subject');
                              if (groupedBySubject && expandedSubjects.size === 0) {
                                setExpandedSubjects(new Set(groupedBySubject.map((g) => g.subject.value)));
                              }
                            } else if (id === 'iteration') {
                              setGrouping('iteration');
                              if (groupedByIteration && expandedIterations.size === 0) {
                                setExpandedIterations(new Set(groupedByIteration.map((g) => g.key)));
                              }
                            }
                          }}
                        />

                        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <Tag size="xs" color="purple">
                                Showing {grouping === 'flat' ? Math.min(flatLimit, sortedFilteredQuads.length) : Math.min(groupLimit, totalGroupCount)} of {grouping === 'flat' ? sortedFilteredQuads.length : totalGroupCount} {groupUnitName}
                                {filteredQuads.length !== quads.length ? ` (filtered from ${quads.length})` : ''}
                            </Tag>
                        </div>
                    </div>
                </div>

                {/* View Mode: Group by Subject, Group by Iteration, or Flat Infinite Table */}
                {grouping === 'subject' && groupedBySubject ? (
                    <GroupBySubjectTable
                        groupedBySubject={groupedBySubject}
                        expandedSubjects={expandedSubjects}
                        toggleSubjectExpanded={toggleSubjectExpanded}
                        expandAllSubjects={expandAllSubjects}
                        collapseAllSubjects={collapseAllSubjects}
                        prefixMap={prefixMap}
                        showGraphColumn={showGraphColumn}
                        showTargetColumn={showTargetColumn}
                        totalCols={totalCols}
                        groupLimit={groupLimit}
                        onLoadMore={handleLoadMoreGroups}
                        isRdfVisualizer={isRdfVisualizer}
                    />
                ) : !isRdfVisualizer && grouping === 'iteration' && groupedByIteration ? (
                    <GroupByIterationTable
                        groupedByIteration={groupedByIteration}
                        expandedIterations={expandedIterations}
                        toggleIterationExpanded={toggleIterationExpanded}
                        expandAllIterations={expandAllIterations}
                        collapseAllIterations={collapseAllIterations}
                        prefixMap={prefixMap}
                        showGraphColumn={showGraphColumn}
                        showTargetColumn={showTargetColumn}
                        totalCols={totalCols}
                        groupLimit={groupLimit}
                        onLoadMore={handleLoadMoreGroups}
                    />
                ) : (
                    <div 
                        className="bk-lv-table-container"
                        style={{ flex: 1, minHeight: 0, overflow: 'auto' }}
                        onScroll={handleFlatScroll}
                    >
                        <table className="bk-table-custom" style={{ width: '100%', borderCollapse: 'collapse' }}>
                            <thead>
                                {table.getHeaderGroups().map(headerGroup => (
                                    <tr key={headerGroup.id}>
                                        {headerGroup.headers.map(header => (
                                            <th 
                                                key={header.id}
                                                style={{ 
                                                    width: header.id === '__index' ? 75 : undefined,
                                                    userSelect: 'none'
                                                }}
                                            >
                                                {flexRender(header.column.columnDef.header, header.getContext())}
                                            </th>
                                        ))}
                                    </tr>
                                ))}
                            </thead>
                            <tbody>
                                {table.getRowModel().rows.map(row => (
                                    <tr key={row.id} className="bk-lv-tr">
                                        {row.getVisibleCells().map(cell => (
                                            <td key={cell.id} className={cell.column.id === '__index' ? 'bk-lv-td bk-lv-td-index-cell' : 'bk-lv-td'}>
                                                {flexRender(cell.column.columnDef.cell, cell.getContext())}
                                            </td>
                                        ))}
                                    </tr>
                                ))}
                                {sortedFilteredQuads.length === 0 && (
                                    <tr>
                                        <td colSpan={columns.length} style={{ textAlign: 'center', padding: '24px', color: 'var(--vscode-disabledForeground)' }}>
                                            No quads found.
                                        </td>
                                    </tr>
                                )}
                            </tbody>
                        </table>
                        {flatLimit < sortedFilteredQuads.length && (
                            <div style={{ textAlign: 'center', padding: '12px', fontSize: '12px', color: 'var(--vscode-descriptionForeground)' }}>
                                Loading more rows...
                            </div>
                        )}
                    </div>
                )}
            </div>
        </TableFilterContext.Provider>
    );
};

