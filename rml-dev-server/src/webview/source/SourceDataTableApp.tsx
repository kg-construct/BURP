import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {Alert, Button, Tag} from 'baukasten-ui/core';
import {getVsCodeApi} from '../common/vscode';
import {
    ColumnDef,
    ColumnFiltersState,
    createColumnHelper,
    getCoreRowModel,
    getFilteredRowModel,
    getSortedRowModel,
    SortingState,
    useReactTable
} from '@tanstack/react-table';
import {SourceDataResult} from '../../generated/types';
import {SourceFromWebviewMessage, SourceToWebviewMessage} from "../protocol/source";
import {AbstractLogicalSourceTableView} from "./AbstractLogicalSourceTableView";
import {AbstractLogicalSourceListView} from "./AbstractLogicalSourceListView";

const vscodeApi = getVsCodeApi<SourceFromWebviewMessage>();

export const AbstractLogicalSourceApp: React.FC = () => {
    const [previewData, setPreviewData] = useState<SourceDataResult | null>(null);
    const [rows, setRows] = useState<(string | null)[][]>([]);
    const [cellTypes, setCellTypes] = useState<(string | null)[][]>([]);
    const [docItems, setDocItems] = useState<string[]>([]);
    const [loading, setLoading] = useState<boolean>(true);
    const [loadingMore, setLoadingMore] = useState<boolean>(false);
    const [error, setError] = useState<string | null>(null);

    const [sorting, setSorting] = useState<SortingState>([]);
    const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([]);
    const [jumpIndex, setJumpIndex] = useState<string>('');
    const [highlightedIndex, setHighlightedIndex] = useState<number | null>(null);
    const [pendingJumpIndex, setPendingJumpIndex] = useState<number | null>(null);

    useEffect(() => {
        const handler = (event: MessageEvent<SourceToWebviewMessage>) => {
            const msg = event.data;
            if (msg.command === 'renderPreview') {
                const data: SourceDataResult = msg.data;
                setPreviewData(data);
                if (data.format === 'json' || data.format === 'xml') {
                    const newItems = data.document?.items || [];
                    if (data.offset === 0) {
                        setDocItems(newItems);
                    } else {
                        setDocItems(prev => [...prev, ...newItems]);
                    }
                } else {
                    const newRows = data.table?.rows || [];
                    const newCellTypes = data.table?.cellTypes || [];
                    if (data.offset === 0) {
                        setRows(newRows);
                        setCellTypes(newCellTypes);
                    } else {
                        setRows(prev => [...prev, ...newRows]);
                        setCellTypes(prev => [...prev, ...newCellTypes]);
                    }
                }
                setLoading(false);
                setLoadingMore(false);
                if (data.diagnostics && data.diagnostics.length > 0) {
                    setError(data.diagnostics.join('; '));
                } else {
                    setError(null);
                }
            } else if (msg.command === 'setLoading') {
                if (msg.isInitial) {
                    setLoading(true);
                    setError(null);
                } else {
                    setLoadingMore(true);
                }
            } else if (msg.command === 'setError') {
                setError(msg.error);
                setLoading(false);
                setLoadingMore(false);
            }
        };

        window.addEventListener('message', handler);
        vscodeApi.postMessage({command: 'ready'});
        return () => window.removeEventListener('message', handler);
    }, []);

    const currentCount = (previewData?.format === 'json' || previewData?.format === 'xml')
        ? docItems.length
        : rows.length;

    useEffect(() => {
        if (pendingJumpIndex !== null) {
            const count = (previewData?.format === 'json' || previewData?.format === 'xml')
                ? docItems.length
                : rows.length;
            if (pendingJumpIndex < count) {
                const targetIdx = pendingJumpIndex;
                setHighlightedIndex(targetIdx);
                setPendingJumpIndex(null);
                setTimeout(() => {
                    const el = scrollContainerRef.current?.querySelector(`[data-index="${targetIdx}"]`);
                    if (el) {
                        el.scrollIntoView({behavior: 'smooth', block: 'center'});
                    }
                }, 60);
            }
        }
    }, [docItems, rows, pendingJumpIndex, previewData?.format]);

    const fetchMoreOnBottomReached = useCallback(
        (containerRefElement?: HTMLDivElement | null) => {
            if (containerRefElement) {
                const {scrollHeight, scrollTop, clientHeight} = containerRefElement;
                if (scrollHeight - scrollTop - clientHeight < 120) {
                    if (!loading && !loadingMore && previewData?.hasMore) {
                        setLoadingMore(true);
                        vscodeApi.postMessage({
                            command: 'fetchMore',
                            offset: currentCount,
                            limit: 50
                        });
                    }
                }
            }
        },
        [loading, loadingMore, previewData?.hasMore, currentCount]
    );

    const handleJump = () => {
        const idx = parseInt(jumpIndex, 10);
        if (isNaN(idx) || idx <= 0) return;
        const total = previewData?.totalItems || currentCount;
        const clampedIdx = previewData?.totalExact ? Math.min(idx, total) : idx;
        const targetIdx = clampedIdx - 1;

        if (targetIdx < currentCount) {
            setHighlightedIndex(targetIdx);
            const el = document.querySelector(`[data-index="${targetIdx}"]`);
            if (el) {
                el.scrollIntoView({behavior: 'smooth', block: 'center'});
            }
        } else if (previewData?.hasMore || targetIdx < total) {
            setLoadingMore(true);
            setPendingJumpIndex(targetIdx);
            const neededLimit = (targetIdx - currentCount) + 50;
            vscodeApi.postMessage({
                command: 'fetchMore',
                offset: currentCount,
                limit: Math.max(50, neededLimit)
            });
        }
    };

    const handleOpenInEditor = () => {
        if (!previewData) return;
        let content;
        if (previewData.format === 'json') {
            content = '[\n' + docItems.join(',\n') + '\n]';
        } else if (previewData.format === 'xml') {
            content = '<!-- Iterations Preview -->\n<root>\n' + docItems.join('\n') + '\n</root>';
        } else {
            const headers = previewData.table?.headers || [];
            content = headers.join(',') + '\n' + rows.map(r => r.join(',')).join('\n');
        }

        vscodeApi.postMessage({
            command: 'openInEditor',
            content,
            language: previewData.format === 'json' ? 'json' : (previewData.format === 'xml' ? 'xml' : 'csv')
        });
    };


    const getCellClass = (cType: string | null | undefined): string => {
        if (!cType) return 'bk-cell-val bk-cell-string';
        const t = cType.toLowerCase();
        if (t.includes('int') || t.includes('decimal') || t.includes('float') || t.includes('double') || t.includes('number')) {
            return 'bk-cell-val bk-cell-number';
        }
        if (t.includes('bool')) {
            return 'bk-cell-val bk-cell-boolean';
        }
        if (t === 'iri' || t.includes('http')) {
            return 'bk-cell-val bk-cell-iri';
        }
        if (t === 'bnode') {
            return 'bk-cell-val bk-cell-bnode';
        }
        if (t === 'json') {
            return 'bk-cell-val bk-cell-json';
        }
        return 'bk-cell-val bk-cell-string';
    };

    const columns = useMemo<ColumnDef<any, any>[]>(() => {
        if (!previewData || previewData.format !== 'table' || !previewData.table) return [];
        const helper = createColumnHelper<any>();

        const cols: ColumnDef<any, any>[] = [
            helper.accessor('__index', {
                id: '__index',
                header: '#',
                cell: info => {
                    const rowIdx = info.getValue();
                    return (
                        <div style={{display: 'flex', alignItems: 'center', gap: '4px'}}>
                            <span className="bk-lv-td-index">{rowIdx + 1}</span>
                            <Button
                                variant="ghost" circular
                                onClick={(e) => {
                                    e.stopPropagation();
                                    vscodeApi.postMessage({command: 'traceIteration', iterationIndex: rowIdx});
                                }}
                                title="Trace this iteration">🔍</Button>
                        </div>
                    );
                },
                enableSorting: false,
                enableColumnFilter: false,
                size: 75
            })
        ];

        previewData.table.headers.forEach((header, colIdx) => {
            cols.push(
                helper.accessor((row) => row.__data[colIdx], {
                    id: header,
                    header: () => (
                        <div style={{display: 'flex', flexDirection: 'column', gap: '2px'}}>
                            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                                <span>{header}</span>
                            </div>
                        </div>
                    ),
                    cell: info => {
                        const val = info.getValue();
                        const rowIdx = info.row.original.__index;
                        const cType = cellTypes[rowIdx]?.[colIdx] || previewData.table?.columnTypes?.[header] || 'string';

                        if (val === null || val === undefined) {
                            return <span className="bk-cell-val bk-cell-null">null</span>;
                        }
                        return (
                            <span className={getCellClass(cType)} title={`Type: ${cType}`}>
                                {val}
                            </span>
                        );
                    },
                    filterFn: (row, columnId, filterValue) => {
                        const val = row.getValue(columnId);
                        if (!val) return false;
                        return String(val).toLowerCase().includes(String(filterValue).toLowerCase());
                    },
                    sortingFn: 'alphanumeric'
                })
            );
        });

        return cols;
    }, [previewData, cellTypes]);

    const tableData = useMemo(() => {
        return rows.map((row, idx) => ({
            __index: idx,
            __data: row
        }));
    }, [rows]);

    const table = useReactTable({
        data: tableData,
        columns,
        state: {
            sorting,
            columnFilters
        },
        onSortingChange: setSorting,
        onColumnFiltersChange: setColumnFilters,
        getCoreRowModel: getCoreRowModel(),
        getSortedRowModel: getSortedRowModel(),
        getFilteredRowModel: getFilteredRowModel(),
    });

    const viewLabel = previewData?.structureResource || previewData?.structureId || 'Logical Source';
    const sourceType = previewData?.sourceType || 'Unknown';
    const format = previewData?.format || 'table';

    const getSourceTagColor = (type: string) => {
        switch (type.toLowerCase()) {
            case 'logicalview':
                return 'info';
            case 'csvsource':
                return 'success';
            case 'jsonsourcerfc':
                return 'warning';
            case 'xmlsource':
                return 'orange';
            case 'rdbsource':
                return 'danger';
            case 'sparqlfilesource':
                return 'purple';
            default:
                return 'default';
        }
    };

    return (
        <div className="bk-app-container bk-lv-table-app"
             style={{display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden'}}>
            <div className="bk-header-bar"
                 style={{flexShrink: 0, padding: '8px 12px', borderBottom: '1px solid var(--vscode-panel-border)'}}>
                <div className="bk-header-left"
                     style={{display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap'}}>
                    <Tag size="sm" color={getSourceTagColor(sourceType)}>{sourceType}</Tag>
                    <span className="bk-lv-title" title={viewLabel} style={{fontWeight: 'bold'}}>{viewLabel}</span>
                    {previewData && (
                        <Tag size="xs" color="gray" title={previewData.totalExact
                            ? `Exact count: ${previewData.totalItems} iterations`
                            : `Showing ${currentCount} of ${previewData.totalItems}+ iterations`}>
                            Showing {currentCount} of {previewData.totalItems}{previewData.totalExact ? '' : '+'} items
                        </Tag>
                    )}

                    <div style={{marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: '8px'}}>
                        <Button size="sm" variant="secondary" onClick={handleOpenInEditor}
                                title="Open loaded preview in VS Code editor tab">
                            Open in VS Code Tab
                        </Button>
                        {format === 'table' && columnFilters.length > 0 && (
                            <Button size="sm" variant="ghost" onClick={() => setColumnFilters([])}>
                                Clear Filters
                            </Button>
                        )}
                        <div style={{display: 'flex', alignItems: 'center', gap: '4px'}}>
                            <input
                                type="number"
                                className="bk-input bk-input-sm"
                                placeholder="Go to index..."
                                value={jumpIndex}
                                onChange={(e) => setJumpIndex(e.target.value)}
                                onKeyDown={(e) => e.key === 'Enter' && handleJump()}
                                style={{width: '100px'}}
                            />
                            <Button size="sm" variant="secondary" onClick={handleJump}>Go</Button>
                        </div>
                    </div>
                </div>
            </div>

            {error && (
                <div style={{padding: '8px'}}>
                    <Alert variant="error">
                        {error}
                    </Alert>
                </div>
            )}

            {format === 'table' ? (
                <AbstractLogicalSourceTableView
                    previewData={previewData}
                    rows={rows}
                    cellTypes={cellTypes}
                    columns={columns}
                    table={table}
                    loading={loading}
                    loadingMore={loadingMore}
                    fetchMoreOnBottomReached={fetchMoreOnBottomReached}
                    handleOpenInEditor={handleOpenInEditor}
                    jumpIndex={jumpIndex}
                    setJumpIndex={setJumpIndex}
                    handleJump={handleJump}
                    highlightedIndex={highlightedIndex}
                    setHighlightedIndex={setHighlightedIndex}
                    setColumnFilters={setColumnFilters}
                    columnFilters={columnFilters}
                />
            ) : (
                    <AbstractLogicalSourceListView
                        docItems={docItems}
                        highlightedIndex={highlightedIndex}
                        setHighlightedIndex={setHighlightedIndex}
                        fetchMoreOnBottomReached={fetchMoreOnBottomReached}
                        loading={loading}
                        loadingMore={loadingMore}
                        handleOpenInEditor={handleOpenInEditor}
                        jumpIndex={jumpIndex}
                        setJumpIndex={setJumpIndex}
                        handleJump={handleJump}
                    />
                )
                }</div>
                );
            };
