import React from 'react';
import {flexRender} from '@tanstack/react-table';

type Props = {
  rows: (string | null)[][];
  columns: any[];
  table: any;
  loading: boolean;
  loadingMore: boolean;
  fetchMoreOnBottomReached: (container?: HTMLDivElement | null) => void;
  highlightedIndex: number | null;
  setHighlightedIndex: (idx: number) => void;
  [key: string]: any;
};

export const AbstractLogicalSourceTableView: React.FC<Props> = ({
  rows,
  columns,
  table,
  loading,
  loadingMore,
  fetchMoreOnBottomReached,
  highlightedIndex,
  setHighlightedIndex,
}) => {
  return (
    <div
      className="bk-lv-table-container"
      style={{flex: 1, minHeight: 0, overflow: 'auto'}}
      onScroll={(e) => fetchMoreOnBottomReached(e.currentTarget)}
    >
      <table className="bk-table-custom" style={{width: '100%', borderCollapse: 'collapse'}}>
        <thead>
          {table.getHeaderGroups().map((headerGroup: any) => (
            <tr key={headerGroup.id}>
              {headerGroup.headers.map((header: any) => (
                <th
                  key={header.id}
                  style={{
                    width: header.getSize() !== 150 ? header.getSize() : undefined,
                    cursor: header.column.getCanSort() ? 'pointer' : 'default',
                    userSelect: 'none'
                  }}
                >
                  <div
                    onClick={header.column.getToggleSortingHandler()}
                    style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '4px'}}
                  >
                    {flexRender(header.column.columnDef.header, header.getContext())}
                    {header.column.getIsSorted() && (
                      <span className="bk-sort-caret">{header.column.getIsSorted() === 'desc' ? ' ▼' : ' ▲'}</span>
                    )}
                  </div>
                  {header.column.getCanFilter() && (
                    <div style={{marginTop: '4px'}} onClick={(e) => e.stopPropagation()}>
                      <input
                        type="text"
                        className="bk-input bk-input-xs"
                        placeholder="Filter..."
                        value={(header.column.getFilterValue() ?? '') as string}
                        onChange={(e) => header.column.setFilterValue(e.target.value)}
                        style={{width: '100%', fontSize: '11px', padding: '2px 4px'}}
                      />
                    </div>
                  )}
                </th>
              ))}
            </tr>
          ))}
        </thead>
        <tbody>
          {table.getRowModel().rows.map((row: any) => {
            const isHighlighted = row.original.__index === highlightedIndex;
            return (
              <tr
                key={row.id}
                data-index={row.original.__index}
                className={`bk-lv-tr ${isHighlighted ? 'bk-lv-tr-highlighted' : ''}`}
                onClick={() => setHighlightedIndex(row.original.__index)}
              >
                {row.getVisibleCells().map((cell: any) => (
                  <td key={cell.id} className="bk-lv-td">
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </td>
                ))}
              </tr>
            );
          })}
          {loading && rows.length === 0 && (
            <tr>
              <td colSpan={columns.length || 1} style={{textAlign: 'center', padding: '24px'}}>
                Loading preview data...
              </td>
            </tr>
          )}
          {!loading && rows.length === 0 && (
            <tr>
              <td
                colSpan={columns.length || 1}
                style={{textAlign: 'center', padding: '24px', color: 'var(--vscode-disabledForeground)'}}
              >
                No iterations found.
              </td>
            </tr>
          )}
        </tbody>
      </table>
      {loadingMore && (
        <div style={{textAlign: 'center', padding: '12px', fontSize: '12px', color: 'var(--vscode-descriptionForeground)'}}>
          Loading more rows...
        </div>
      )}
    </div>
  );
};
