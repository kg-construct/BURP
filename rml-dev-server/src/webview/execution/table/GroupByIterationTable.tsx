import React from 'react';
import { Button } from 'baukasten-ui/core';
import { ParsedQuadItem } from '../nquadsParser';
import { vscodeApi } from '../../common/vscode';
import { GroupedQuadTable } from './GroupedQuadTable';

export interface GroupedIterationItem {
    key: string;
    iterationIndex: number;
    logicalSourceId: string | null;
    logicalSourceHumanId: string;
    quads: ParsedQuadItem[];
}

export interface GroupByIterationTableProps {
    groupedByIteration: GroupedIterationItem[];
    expandedIterations: Set<string>;
    toggleIterationExpanded: (key: string) => void;
    expandAllIterations: () => void;
    collapseAllIterations: () => void;
    prefixMap?: Record<string, string>;
    showTargetColumn?: boolean;
    showGraphColumn?: boolean;
    totalCols: number;
    groupLimit?: number;
    onLoadMore?: () => void;
}

export const GroupByIterationTable: React.FC<GroupByIterationTableProps> = ({
    groupedByIteration,
    expandedIterations,
    toggleIterationExpanded,
    expandAllIterations,
    collapseAllIterations,
    prefixMap,
    showGraphColumn = false,
    showTargetColumn = false,
    totalCols,
    groupLimit = 50,
    onLoadMore,
}) => {
    return (
        <GroupedQuadTable<GroupedIterationItem>
            groups={groupedByIteration}
            getGroupKey={(g) => g.key}
            getGroupQuads={(g) => g.quads}
            expandedKeys={expandedIterations}
            toggleExpanded={toggleIterationExpanded}
            expandAll={expandAllIterations}
            collapseAll={collapseAllIterations}
            hideSubjectInRows={false}
            prefixMap={prefixMap}
            showGraphColumn={showGraphColumn}
            showTargetColumn={showTargetColumn}
            totalCols={totalCols}
            groupLimit={groupLimit}
            onLoadMore={onLoadMore}
            entityNamePlural="iterations"
            renderGroupHeaderActions={(group) => (
                <Button
                    variant="ghost"
                    circular
                    size="xs"
                    onClick={(e) => {
                        e.stopPropagation();
                        vscodeApi.postMessage({
                            command: 'traceIteration',
                            iterationIndex: group.iterationIndex
                        });
                    }}
                    title={`Trace entire iteration #${group.iterationIndex}`}
                >
                    🔍
                </Button>
            )}
            renderGroupHeaderContent={(group) => (
                <>
                    <span className="--bk-font-weight-medium">Iteration #{group.iterationIndex}</span>
                    <span>{group.logicalSourceHumanId || group.logicalSourceId}</span>
                </>
            )}
        />
    );
};