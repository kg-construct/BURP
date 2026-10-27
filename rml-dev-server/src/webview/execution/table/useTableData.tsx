import React, {useEffect, useMemo, useRef, useState} from 'react';
import {ColumnDef, SortingState} from '@tanstack/react-table';
import {Term} from 'n3';
import {ParsedQuadItem} from '../nquadsParser';
import {shortenIri} from '../rdfUtils';
import {TermRenderer, GraphRenderer} from '../../common/TermRenderer';
import {TableFilterContextValue} from './TableFilterContext';
import {SubjectHeader, PredicateHeader, ObjectHeader, GraphHeader, TargetHeader} from './TableHeaders';
import {vscodeApi} from '../../common/vscode';
import {Button} from "baukasten-ui/core";
import {GroupedIterationItem} from "./GroupByIterationTable";
import {GroupedSubjectItem} from "./GroupBySubjectTable";
import {webviewTelemetry} from "../../common/telemetry";

function useDebouncedSearchTelemetry(
    value: string,
    column: 'subject' | 'predicate' | 'object' | 'graph' | 'target'
) {
    const prevValue = useRef(value);
    useEffect(() => {
        if (prevValue.current === value) return;
        const timer = setTimeout(() => {
            const isActive = value.trim().length > 0;
            const wasActive = prevValue.current.trim().length > 0;
            if (isActive || wasActive) {
                webviewTelemetry.capture('execution_filter_applied', {
                    filter_kind: 'search',
                    column,
                    filter_type: 'text',
                    is_active: isActive
                });
            }
            prevValue.current = value;
        }, 500);
        return () => clearTimeout(timer);
    }, [value, column]);
}

function useFilterTelemetry(
    value: string,
    column: 'subject' | 'object',
    filterType: 'term_type' | 'datatype' | 'language'
) {
    const prevValue = useRef(value);
    useEffect(() => {
        if (prevValue.current === value) return;
        const isActive = value !== 'all';
        const wasActive = prevValue.current !== 'all';
        if (isActive || wasActive) {
            webviewTelemetry.capture('execution_filter_applied', {
                filter_kind: 'filter',
                column,
                filter_type: filterType,
                is_active: isActive
            });
        }
        prevValue.current = value;
    }, [value, column, filterType]);
}

export interface UseTableDataProps {
    quads: ParsedQuadItem[];
    prefixMap?: Record<string, string>;
    logicalSources?: Record<string, string>;
    showGraphColumn?: boolean;
    showTargetColumn?: boolean;
    isRdfVisualizer?: boolean;
}

/**
 * Manages table data transformations, filtering, sorting, column definitions,
 * and grouping (flat, by-subject, or by-iteration).
 */
export function useTableData({
                                 quads,
                                 prefixMap,
                                 logicalSources,
                                 showGraphColumn = false,
                                 showTargetColumn = false,
                                 isRdfVisualizer = false
                             }: UseTableDataProps) {
    const [grouping, setGrouping] = useState<'flat' | 'subject' | 'iteration'>('flat');
    const [subjectSearch, setSubjectSearch] = useState<string>('');
    const [subjectType, setSubjectType] = useState<string>('all');
    const [predicateSearch, setPredicateSearch] = useState<string>('');
    const [objectSearch, setObjectSearch] = useState<string>('');
    const [objectType, setObjectType] = useState<string>('all');
    const [selectedDatatype, setSelectedDatatype] = useState<string>('all');
    const [selectedLanguage, setSelectedLanguage] = useState<string>('all');
    const [graphSearch, setGraphSearch] = useState<string>('');
    const [targetSearch, setTargetSearch] = useState<string>('');
    const [expandedSubjects, setExpandedSubjects] = useState<Set<string>>(new Set());
    const [expandedIterations, setExpandedIterations] = useState<Set<string>>(new Set());
    const [sorting, setSorting] = useState<SortingState>([]);

    useDebouncedSearchTelemetry(subjectSearch, 'subject');
    useDebouncedSearchTelemetry(predicateSearch, 'predicate');
    useDebouncedSearchTelemetry(objectSearch, 'object');
    useDebouncedSearchTelemetry(graphSearch, 'graph');
    useDebouncedSearchTelemetry(targetSearch, 'target');

    useFilterTelemetry(subjectType, 'subject', 'term_type');
    useFilterTelemetry(objectType, 'object', 'term_type');
    useFilterTelemetry(selectedDatatype, 'object', 'datatype');
    useFilterTelemetry(selectedLanguage, 'object', 'language');

    const toggleSorting = (columnId: string) => {
        setSorting((prev) => {
            const current = prev.find((s) => s.id === columnId);
            if (!current) {
                return [{id: columnId, desc: false}];
            }
            if (!current.desc) {
                return [{id: columnId, desc: true}];
            }
            return [];
        });
    };

    // Extract available datatypes & languages dynamically using functional flatMap + Set
    const availableDatatypes = useMemo(() => {
        return Array.from(new Set(
            quads.flatMap((q) => (q.datatypeIri ? [q.datatypeIri] : []))
        )).sort();
    }, [quads]);

    const availableLanguages = useMemo(() => {
        return Array.from(new Set(
            quads.flatMap((q) => (q.language ? [q.language] : []))
        )).sort();
    }, [quads]);

    // Filter quads based on per-column filters
    const filteredQuads = useMemo(() => {
        const sQ = subjectSearch.trim().toLowerCase();
        const pQ = predicateSearch.trim().toLowerCase();
        const oQ = objectSearch.trim().toLowerCase();
        const gQ = graphSearch.trim().toLowerCase();
        const tQ = targetSearch.trim().toLowerCase();

        return quads.filter((q) => {
            if (sQ) {
                const sVal = q.subject.value.toLowerCase();
                const sDisplay = shortenIri(q.subject.value, prefixMap).display.toLowerCase();
                if (!sVal.includes(sQ) && !sDisplay.includes(sQ)) {
                    return false;
                }
            }
            if (subjectType !== 'all' && q.subject.termType !== subjectType) {
                return false;
            }

            if (pQ) {
                const pVal = q.predicate.value.toLowerCase();
                const pDisplay = shortenIri(q.predicate.value, prefixMap).display.toLowerCase();
                if (!pVal.includes(pQ) && !pDisplay.includes(pQ)) {
                    return false;
                }
            }

            if (oQ) {
                const oVal = q.object.value.toLowerCase();
                const oDisplay = shortenIri(q.object.value, prefixMap).display.toLowerCase();
                if (!oVal.includes(oQ) && !oDisplay.includes(oQ)) {
                    return false;
                }
            }

            if (objectType !== 'all') {
                if (q.objectType !== objectType) {
                    return false;
                }
                if (objectType === 'Literal') {
                    if (selectedDatatype !== 'all' && q.datatypeIri !== selectedDatatype) {
                        return false;
                    }
                    if (selectedLanguage !== 'all' && q.language !== selectedLanguage) {
                        return false;
                    }
                }
            }

            if (showGraphColumn && gQ) {
                const gVal = q.graphName.toLowerCase();
                const gDisplay = shortenIri(q.graphName, prefixMap).display.toLowerCase();
                if (!gVal.includes(gQ) && !gDisplay.includes(gQ)) {
                    return false;
                }
            }

            if (showTargetColumn && tQ) {
                const tVal = (q.targetId === 'default' ? 'default' : (q.targetId || 'default')).toLowerCase();
                if (!tVal.includes(tQ)) {
                    return false;
                }
            }

            return true;
        });
    }, [
        quads,
        prefixMap,
        subjectSearch,
        subjectType,
        predicateSearch,
        objectSearch,
        objectType,
        selectedDatatype,
        selectedLanguage,
        graphSearch,
        targetSearch,
        showGraphColumn,
        showTargetColumn
    ]);

    // Sort filtered quads
    const sortedFilteredQuads = useMemo(() => {
        if (!sorting.length) return filteredQuads;
        const sort = sorting[0];
        const mult = sort.desc ? -1 : 1;
        return [...filteredQuads].sort((a, b) => {
            let aVal = '';
            let bVal = '';
            if (sort.id === 'subject') {
                aVal = a.subject.value;
                bVal = b.subject.value;
            } else if (sort.id === 'predicate') {
                aVal = a.predicate.value;
                bVal = b.predicate.value;
            } else if (sort.id === 'object') {
                aVal = a.object.value;
                bVal = b.object.value;
            } else if (sort.id === 'graph') {
                aVal = a.graphName;
                bVal = b.graphName;
            } else if (sort.id === 'target') {
                aVal = a.targetId || 'default';
                bVal = b.targetId || 'default';
            }
            return aVal.localeCompare(bVal) * mult;
        });
    }, [filteredQuads, sorting]);

    // Group by subject (functional Map.groupBy)
    const groupedBySubject = useMemo(() => {
        if (grouping !== 'subject') {
            return null;
        }
        const groups = Map.groupBy(sortedFilteredQuads, (q) => q.subject.value);
        return Array.from(groups.values(), (quads) => ({
            subject: quads[0].subject,
            quads
        }));
    }, [sortedFilteredQuads, grouping]);

    // Group by iteration (functional Map.groupBy)
    const groupedByIteration = useMemo(() => {
        if (grouping !== 'iteration') {
            return null;
        }
        const groups = Map.groupBy(sortedFilteredQuads, (q) => {
            const logicalSourceId = q.lineage?.primary.logicalSourceId ?? null;
            const iterationIndex = q.lineage?.primary?.index ?? 0;
            return `${logicalSourceId}#${iterationIndex}`;
        });
        return Array.from(groups.entries(), ([key, quads]) => {
            const primary = quads[0].lineage?.primary;
            const logicalSourceId = primary?.logicalSourceId ?? null;
            const logicalSourceHumanId = (logicalSourceId && logicalSources ? logicalSources[logicalSourceId] : null) ?? logicalSourceId ?? 'unknown';
            const iterationIndex = primary?.index ?? 0;
            return {
                key,
                iterationIndex,
                logicalSourceId,
                logicalSourceHumanId,
                quads
            };
        });
    }, [sortedFilteredQuads, grouping, logicalSources]);

    const toggleSubjectExpanded = (subjVal: string) => {
        setExpandedSubjects((prev) => {
            const next = new Set(prev);
            if (next.has(subjVal)) {
                next.delete(subjVal);
            } else {
                next.add(subjVal);
            }
            return next;
        });
    };

    const expandAllSubjects = () => {
        if (!groupedBySubject) return;
        setExpandedSubjects(new Set(groupedBySubject.map((g) => g.subject.value)));
    };

    const collapseAllSubjects = () => {
        setExpandedSubjects(new Set());
    };

    const toggleIterationExpanded = (iterKey: string) => {
        setExpandedIterations((prev) => {
            const next = new Set(prev);
            if (next.has(iterKey)) {
                next.delete(iterKey);
            } else {
                next.add(iterKey);
            }
            return next;
        });
    };

    const expandAllIterations = () => {
        if (!groupedByIteration) return;
        setExpandedIterations(new Set(groupedByIteration.map((g) => g.key)));
    };

    const collapseAllIterations = () => {
        setExpandedIterations(new Set());
    };

    const filterContextValue = useMemo<TableFilterContextValue>(() => ({
        subjectSearch,
        setSubjectSearch,
        subjectType,
        setSubjectType,
        predicateSearch,
        setPredicateSearch,
        objectSearch,
        setObjectSearch,
        objectType,
        setObjectType,
        selectedDatatype,
        setSelectedDatatype,
        selectedLanguage,
        setSelectedLanguage,
        graphSearch,
        setGraphSearch,
        targetSearch,
        setTargetSearch,
        availableDatatypes,
        availableLanguages,
        prefixMap,
        sorting,
        toggleSorting
    }), [
        subjectSearch,
        subjectType,
        predicateSearch,
        objectSearch,
        objectType,
        selectedDatatype,
        selectedLanguage,
        graphSearch,
        targetSearch,
        availableDatatypes,
        availableLanguages,
        prefixMap,
        sorting
    ]);

    const columns = useMemo<ColumnDef<ParsedQuadItem, any>[]>(() => {
        const cols: ColumnDef<ParsedQuadItem, any>[] = [
            {
                id: '__index',
                header: () => <span className="bk-lv-th-index">#</span>,
                cell: ({row}) => {
                    const q = row.original;
                    return (
                        <div style={{display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '4px'}}>
                            <span className="bk-lv-td-index">{row.index + 1}</span>
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
                                >
                                    🔍
                                </Button>
                            )}
                        </div>
                    );
                },
                size: 75,
                enableSorting: false
            },
            {
                id: 'subject',
                accessorFn: (row) => row.subject.value,
                header: SubjectHeader,
                cell: ({row}) => <TermRenderer term={row.original.subject} prefixMap={prefixMap}/>
            },
            {
                id: 'predicate',
                accessorFn: (row) => row.predicate.value,
                header: PredicateHeader,
                cell: ({row}) => <TermRenderer term={row.original.predicate} isPredicate prefixMap={prefixMap}/>
            },
            {
                id: 'object',
                accessorFn: (row) => row.object.value,
                header: ObjectHeader,
                cell: ({row}) => (
                    <TermRenderer
                        term={row.original.object}
                        isObject
                        datatype={row.original.datatypeIri}
                        language={row.original.language}
                        prefixMap={prefixMap}
                    />
                )
            }
        ];

        if (showGraphColumn) {
            cols.push({
                id: 'graph',
                accessorFn: (row) => row.graphName,
                header: GraphHeader,
                cell: ({row}) => <GraphRenderer graphName={row.original.graphName} prefixMap={prefixMap}/>
            });
        }

        if (showTargetColumn) {
            cols.push({
                id: 'target',
                accessorFn: (row) => row.targetId || 'default',
                header: TargetHeader,
                cell: ({row}) => (
                    <span className="bk-target-cell-badge">
                        {row.original.targetId === 'default' ? 'Default' : (row.original.targetId || 'Default')}
                    </span>
                )
            });
        }

        return cols;
    }, [prefixMap, showGraphColumn, showTargetColumn]);

    return {
        grouping,
        setGrouping,
        expandedSubjects,
        setExpandedSubjects,
        expandedIterations,
        setExpandedIterations,
        sorting,
        setSorting,
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
        totalCols: 4 + (showGraphColumn ? 1 : 0) + (showTargetColumn ? 1 : 0)
    };
}
