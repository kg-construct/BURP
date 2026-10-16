import { Button } from 'baukasten-ui/core';
import { Term } from 'n3';
import { ParsedQuadItem } from '../nquadsParser';
import { TermRenderer } from '../../common/TermRenderer';
import { vscodeApi } from '../../common/vscode';
import { GroupedQuadTable } from './GroupedQuadTable';

export interface GroupedSubjectItem {
    subject: Term;
    quads: ParsedQuadItem[];
}

export interface GroupBySubjectTableProps {
    groupedBySubject: GroupedSubjectItem[];
    expandedSubjects: Set<string>;
    toggleSubjectExpanded: (subjVal: string) => void;
    expandAllSubjects: () => void;
    collapseAllSubjects: () => void;
    prefixMap?: Record<string, string>;
    showTargetColumn?: boolean;
    showGraphColumn?: boolean;
    totalCols: number;
    isRdfVisualizer?: boolean;
    groupLimit?: number;
    onLoadMore?: () => void;
}

export const GroupBySubjectTable: React.FC<GroupBySubjectTableProps> = ({
    groupedBySubject,
    expandedSubjects,
    toggleSubjectExpanded,
    expandAllSubjects,
    collapseAllSubjects,
    prefixMap,
    showGraphColumn = false,
    showTargetColumn = false,
    totalCols,
    groupLimit = 50,
    onLoadMore,
    isRdfVisualizer = false,
}) => {
    return (
        <GroupedQuadTable<GroupedSubjectItem>
            groups={groupedBySubject}
            getGroupKey={(g) => g.subject.value}
            getGroupQuads={(g) => g.quads}
            expandedKeys={expandedSubjects}
            toggleExpanded={toggleSubjectExpanded}
            expandAll={expandAllSubjects}
            collapseAll={collapseAllSubjects}
            hideSubjectInRows={true}
            prefixMap={prefixMap}
            showGraphColumn={showGraphColumn}
            showTargetColumn={showTargetColumn}
            totalCols={totalCols}
            groupLimit={groupLimit}
            onLoadMore={onLoadMore}
            isRdfVisualizer={isRdfVisualizer}
            entityNamePlural="unique subjects"
            renderGroupHeaderActions={(group) =>
                !isRdfVisualizer && (
                    <Button
                        variant="ghost"
                        circular
                        size="xs"
                        onClick={(e) => {
                            e.stopPropagation();
                            const q = group.quads[0];
                            if (q) {
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
                            }
                        }}
                        title="Trace Provenance of this subject's statement"
                    >
                        🔍
                    </Button>
                )
            }
            renderGroupHeaderContent={(group) => (
                <TermRenderer term={group.subject} prefixMap={prefixMap} />
            )}
        />
    );
};
