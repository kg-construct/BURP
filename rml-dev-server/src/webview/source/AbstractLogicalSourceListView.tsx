import React from 'react';
import {Button} from 'baukasten-ui/core';
import {SourceFromWebviewMessage} from '../protocol/source';
import {getVsCodeApi} from '../common/vscode';

type Props = {
  docItems: string[];
  highlightedIndex: number | null;
  setHighlightedIndex: (idx: number) => void;
  fetchMoreOnBottomReached: (container?: HTMLDivElement | null) => void;
  loading: boolean;
  loadingMore: boolean;
  handleOpenInEditor: () => void;
  jumpIndex: string;
  setJumpIndex: (v: string) => void;
  handleJump: () => void;
};

export const AbstractLogicalSourceListView: React.FC<Props> = ({
  docItems,
  highlightedIndex,
  setHighlightedIndex,
  fetchMoreOnBottomReached,
  loading,
  loadingMore,
  handleOpenInEditor,
  jumpIndex,
  setJumpIndex,
  handleJump,
}) => {
  const vscodeApi = getVsCodeApi<SourceFromWebviewMessage>();
  return (
    <div
      className="bk-doc-viewer-scroll"
      style={{flex: 1, minHeight: 0, overflow: 'auto', padding: '12px'}}
      onScroll={(e) => fetchMoreOnBottomReached(e.currentTarget)}
    >
      {docItems.map((item, idx) => {
        const isHighlighted = idx === highlightedIndex;
        return (
          <div
            key={idx}
            data-index={idx}
            className={`bk-doc-item-card ${isHighlighted ? 'bk-doc-item-card-highlighted' : ''}`}
            onClick={() => setHighlightedIndex(idx)}
          >
            <div className="bk-doc-item-header" style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
              <span>Iteration #{idx + 1}</span>
              <Button variant="ghost" circular size="xs" onClick={(e) => {
                e.stopPropagation();
                vscodeApi.postMessage({command: 'traceIteration', iterationIndex: idx});
              }} title="Trace this iteration">🔍</Button>
            </div>
            <pre className="bk-doc-item-body"><code>{item}</code></pre>
          </div>
        );
      })}
      {loading && docItems.length === 0 && (
        <div style={{textAlign: 'center', padding: '24px'}}>Loading document preview...</div>
      )}
      {!loading && docItems.length === 0 && (
        <div style={{textAlign: 'center', padding: '24px', color: 'var(--vscode-disabledForeground)'}}>No iterations found.</div>
      )}
      {loadingMore && (
        <div style={{textAlign: 'center', padding: '12px', fontSize: '12px', color: 'var(--vscode-descriptionForeground)'}}>
          Loading more items...
        </div>
      )}
    </div>
  );
};
