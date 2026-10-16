import React from 'react';
import { Button, Tag } from 'baukasten-ui/core';
import { PartitionOption, PartitionSelector } from '../../common/PartitionSelector';
import { GraphNode } from './graphModel';

export interface GraphControlsBarProps {
    layoutMode: 'list' | 'organic';
    setLayoutMode: (mode: 'list' | 'organic') => void;
    targetOptions?: PartitionOption[];
    selectedTargetId?: string;
    onSelectTarget?: (targetId: string) => void;
    graphOptions?: PartitionOption[];
    selectedGraphId?: string;
    onSelectGraph?: (graphId: string) => void;
    searchQuery: string;
    setSearchQuery: (query: string) => void;
    matchingNodes: GraphNode[];
    activeMatchIndex: number;
    setActiveMatchIndex: React.Dispatch<React.SetStateAction<number>>;
    pinnedCount: number;
    onUnpinAll: () => void;
    nodeCount: number;
    linkCount: number;
}

export const GraphControlsBar: React.FC<GraphControlsBarProps> = ({
    layoutMode,
    setLayoutMode,
    targetOptions,
    selectedTargetId,
    onSelectTarget,
    graphOptions,
    selectedGraphId,
    onSelectGraph,
    searchQuery,
    setSearchQuery,
    matchingNodes,
    activeMatchIndex,
    setActiveMatchIndex,
    pinnedCount,
    onUnpinAll,
    nodeCount,
    linkCount
}) => {
    return (
        <div className="bk-graph-controls-bar">
            <div className="bk-graph-controls-left">
                {/* Layout Selector */}
                <div className="bk-segmented-switch">
                    <button
                        type="button"
                        className={`bk-segmented-btn ${layoutMode === 'list' ? 'active' : ''}`}
                        onClick={() => setLayoutMode('list')}
                        title="List of Organic (Per Iteration / Subject)"
                    >
                        ☷ List
                    </button>
                    <button
                        type="button"
                        className={`bk-segmented-btn ${layoutMode === 'organic' ? 'active' : ''}`}
                        onClick={() => setLayoutMode('organic')}
                        title="Free Organic Cluster"
                    >
                        ፨ Organic
                    </button>
                </div>

                {/* Partition Selectors (Target & Graph) */}
                {targetOptions && targetOptions.length > 1 && onSelectTarget && (
                    <PartitionSelector
                        label="Target"
                        options={targetOptions}
                        value={selectedTargetId || 'all'}
                        onChange={onSelectTarget}
                        compact={true}
                    />
                )}
                {graphOptions && graphOptions.length > 1 && onSelectGraph && (
                    <PartitionSelector
                        label="Graph"
                        options={graphOptions}
                        value={selectedGraphId || 'all'}
                        onChange={onSelectGraph}
                        compact={true}
                    />
                )}

                {/* Search Bar (Locate & Zoom, not filter) */}
                <div className="bk-graph-search-box">
                    <span className="bk-graph-search-icon">🔍</span>
                    <input
                        type="text"
                        className="bk-graph-search-input"
                        placeholder="Search node..."
                        value={searchQuery}
                        onChange={(e) => {
                            setSearchQuery(e.target.value);
                            setActiveMatchIndex(0);
                        }}
                    />
                    {matchingNodes.length > 0 && (
                        <span className="bk-search-match-count">
                            {activeMatchIndex + 1}/{matchingNodes.length}
                        </span>
                    )}
                    {searchQuery && (
                        <div className="bk-search-nav-btns">
                            <button
                                type="button"
                                className="bk-search-nav-btn"
                                disabled={matchingNodes.length <= 1}
                                onClick={() =>
                                    setActiveMatchIndex((prev) =>
                                        prev > 0 ? prev - 1 : matchingNodes.length - 1
                                    )
                                }
                                title="Previous match"
                            >
                                ▲
                            </button>
                            <button
                                type="button"
                                className="bk-search-nav-btn"
                                disabled={matchingNodes.length <= 1}
                                onClick={() =>
                                    setActiveMatchIndex((prev) =>
                                        prev < matchingNodes.length - 1 ? prev + 1 : 0
                                    )
                                }
                                title="Next match"
                            >
                                ▼
                            </button>
                            <button
                                type="button"
                                className="bk-search-nav-btn"
                                onClick={() => {
                                    setSearchQuery('');
                                    setActiveMatchIndex(0);
                                }}
                                title="Clear search"
                            >
                                ✕
                            </button>
                        </div>
                    )}
                </div>

                {/* Unpin All Button */}
                {pinnedCount > 0 && (
                    <Button size="sm" variant="secondary" onClick={onUnpinAll} title="Unpin all nodes">
                        📌 Unpin All ({pinnedCount})
                    </Button>
                )}
            </div>

            <div className="bk-graph-controls-right">
                <Tag size="xs" color="purple">
                    {nodeCount} nodes • {linkCount} links
                </Tag>
            </div>
        </div>
    );
};
