import React, { useEffect } from 'react';
import { Alert, Button, Tag } from 'baukasten-ui/core';
import { SegmentedButton } from '../common/SegmentedButton';
import { ExecutionTable } from './ExecutionTable';
import { ExecutionGraph } from './ExecutionGraph';
import { PrefixAccordion } from '../common/PrefixAccordion';
import { PartitionSelector } from '../common/PartitionSelector';
import { useExecution } from './hooks/useExecution';
import { webviewTelemetry } from '../common/telemetry';


// @ts-ignore
import './execution.css';

export interface ExecutionAppProps {
    isRdfVisualizer?: boolean;
}

export const ExecutionApp: React.FC<ExecutionAppProps> = ({ isRdfVisualizer = false }) => {
    useEffect(() => {
        document.body.style.overflow = 'hidden';
        return () => {
            document.body.style.overflow = '';
        };
    }, []);

    const {
        viewMode,
        setViewMode,
        fileName,
        status,
        error,
        prefixes,
        logicalSources,
        defaultHullMode,
        rerun,
        clearCacheAndRerun,
        selectedTargetId,
        setSelectedTargetId,
        selectedGraphId,
        setSelectedGraphId,
        allQuads,
        targetOptions,
        graphOptions,
        displayQuads,
        selectedTargetMeta
    } = useExecution({ isRdfVisualizer });

    const handleViewModeChange = (newMode: 'table' | 'graph') => {
        if (newMode !== viewMode) {
            setViewMode(newMode);
            webviewTelemetry.capture('execution_view_mode_changed', { view_mode: newMode });
        }
    };

    const handleRerun = () => {
        webviewTelemetry.capture('execution_rerun_clicked', { clear_cache: false });
        rerun();
    };

    const handleClearCacheAndRerun = () => {
        webviewTelemetry.capture('execution_rerun_clicked', { clear_cache: true });
        clearCacheAndRerun();
    };

    const handleTargetChange = (targetId: string) => {
        setSelectedTargetId(targetId);
        webviewTelemetry.capture('execution_partition_changed', {
            partition_type: 'target',
            is_all: targetId === 'all',
            partition_id: targetId
        });
    };

    const handleGraphChange = (graphId: string) => {
        setSelectedGraphId(graphId);
        webviewTelemetry.capture('execution_partition_changed', {
            partition_type: 'graph',
            is_all: graphId === 'all',
            partition_id: graphId
        });
    };

    return (
        <div className="bk-app-container bk-execution-app">
            {/* Top Navigation / Status Bar */}
            <div className="bk-header-bar">
                <div className="bk-header-left">
                    <Tag size="sm" color="info">{isRdfVisualizer ? 'RDF Document' : 'Execution'}</Tag>
                    <span className="bk-lv-title" title={fileName}>
                        {fileName || (isRdfVisualizer ? 'RDF Document' : 'RML Mapping')}
                    </span>
                    {status === 'running' && (
                        <Tag size="xs" color="warning">{isRdfVisualizer ? 'Loading...' : 'Running...'}</Tag>
                    )}
                    {status === 'completed' && (
                        <Tag size="xs" color="success">{isRdfVisualizer ? 'Loaded' : 'Finished'}</Tag>
                    )}
                    {status === 'error' && (
                        <Tag size="xs" color="error">Error</Tag>
                    )}
                </div>

                <div className="bk-header-right">
                    <SegmentedButton
                        options={[
                            { id: 'table', label: 'Table', active: viewMode === 'table' },
                            { id: 'graph', label: 'Graph', active: viewMode === 'graph' }
                        ]}
                        onSelect={(id) => handleViewModeChange(id as 'table' | 'graph')}
                    />

                    <Button size="sm" variant="secondary" onClick={handleRerun} disabled={status === 'running'}>
                        {isRdfVisualizer ? 'Reload' : 'Re-run'}
                    </Button>
                    {!isRdfVisualizer && (
                        <Button
                            size="sm"
                            variant="secondary"
                            onClick={handleClearCacheAndRerun}
                            disabled={status === 'running'}
                        >
                            Clear Cache &amp; Run
                        </Button>
                    )}
                </div>
            </div>

            {/* Error banner */}
            {error && (
                <Alert variant="error" title="Execution Error">
                    {error}
                </Alert>
            )}

            {/* Prefix Accordion (accessible in both Table and Graph views) */}
            <PrefixAccordion prefixes={prefixes} />

            {/* View Mode: Table */}
            {viewMode === 'table' && (
                <>
                    {/* Target Selector (hidden if <= 1 target) */}
                    {targetOptions.length > 1 && (
                        <PartitionSelector
                            label="Targets"
                            options={targetOptions}
                            value={selectedTargetId}
                            onChange={handleTargetChange}
                        />
                    )}

                    {/* Target Details Subline (only when a specific non-default target is selected) */}
                    {selectedTargetMeta && (
                        <div className="bk-target-details-subline">
                            <span className="bk-details-item">
                                <span className="bk-details-key">Path:</span>
                                <span className="bk-details-val">{selectedTargetMeta.targetName}</span>
                            </span>
                            {selectedTargetMeta.serialization && (
                                <span className="bk-details-item">
                                    <span className="bk-details-key">Format:</span>
                                    <Tag size="xs" color="info">{selectedTargetMeta.serialization}</Tag>
                                </span>
                            )}
                            {selectedTargetMeta.compression && (
                                <span className="bk-details-item">
                                    <span className="bk-details-key">Compression:</span>
                                    <Tag size="xs" color="secondary">{selectedTargetMeta.compression}</Tag>
                                </span>
                            )}
                            {selectedTargetMeta.encoding && (
                                <span className="bk-details-item">
                                    <span className="bk-details-key">Encoding:</span>
                                    <Tag size="xs" color="default">{selectedTargetMeta.encoding}</Tag>
                                </span>
                            )}
                        </div>
                    )}

                    {/* Graph Selector (hidden if <= 1 graph) */}
                    {graphOptions.length > 1 && (
                        <PartitionSelector
                            label="Graphs"
                            options={graphOptions}
                            value={selectedGraphId}
                            onChange={handleGraphChange}
                        />
                    )}

                    {/* Main Content: Table */}
                    <div className="bk-execution-content">
                        {allQuads.length === 0 && status === 'running' ? (
                            <div className="bk-loading-state">
                                <span className="bk-spinner"></span>
                                <span>{isRdfVisualizer ? 'Loading and parsing RDF document...' : 'Executing mapping and awaiting quads...'}</span>
                            </div>
                        ) : allQuads.length === 0 ? (
                            <div className="bk-empty-state">
                                {error ? (
                                    <span className="bk-empty-error">{error}</span>
                                ) : (
                                    <span>{isRdfVisualizer ? 'No triples or quads found in this RDF document.' : 'No quads produced by this mapping.'}</span>
                                )}
                            </div>
                        ) : (
                            <ExecutionTable
                                quads={displayQuads}
                                prefixMap={prefixes}
                                logicalSources={logicalSources}
                                showGraphColumn={selectedGraphId === 'all' && graphOptions.length > 1}
                                showTargetColumn={selectedTargetId === 'all' && targetOptions.length > 1}
                                isRdfVisualizer={isRdfVisualizer}
                            />
                        )}
                    </div>
                </>
            )}

            {/* View Mode: Graph */}
            {viewMode === 'graph' && (
                <div className="bk-execution-content bk-execution-content-graph">
                    {allQuads.length === 0 && status === 'running' ? (
                        <div className="bk-loading-state">
                            <span className="bk-spinner"></span>
                            <span>{isRdfVisualizer ? 'Loading and parsing RDF document...' : 'Executing mapping and awaiting quads...'}</span>
                        </div>
                    ) : allQuads.length === 0 ? (
                        <div className="bk-empty-state">
                            {error ? (
                                <span className="bk-empty-error">{error}</span>
                            ) : (
                                <span>{isRdfVisualizer ? 'No triples or quads found in this RDF document.' : 'No quads produced by this mapping.'}</span>
                            )}
                        </div>
                    ) : (
                        <ExecutionGraph
                            quads={allQuads}
                            prefixMap={prefixes}
                            targetOptions={targetOptions}
                            selectedTargetId={selectedTargetId}
                            onSelectTarget={handleTargetChange}
                            graphOptions={graphOptions}
                            selectedGraphId={selectedGraphId}
                            onSelectGraph={handleGraphChange}
                            defaultHullMode={defaultHullMode as any}
                        />
                    )}
                </div>
            )}
        </div>
    );
};
