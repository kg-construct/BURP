import { useState, useMemo, useEffect } from 'react';
import { TargetState } from './useExecutionProtocol';
import { ParsedQuadItem } from '../nquadsParser';
import { PartitionOption } from '../../common/PartitionSelector';
import { formatTargetResource, shortenIri } from '../rdfUtils';

export interface UseExecutionPartitionsProps {
    targets: Map<string, TargetState>;
    prefixes: Record<string, string>;
}

/**
 * Manages partition filtering and metadata derivations across multiple logical targets and RDF named graphs.
 *
 * - Decouples partition state (target selection, graph selection) and filtering derivations from UI components.
 * - Computes partition options with quad counts for `PartitionSelector`.
 * - Resolves metadata for active targets (file path, serialization, compression, encoding).
 */
export function useExecutionPartitions({ targets, prefixes }: UseExecutionPartitionsProps) {
    const [selectedTargetId, setSelectedTargetId] = useState<string>('all');
    const [selectedGraphId, setSelectedGraphId] = useState<string>('all');

    // Combine all quads across all targets
    const allQuads = useMemo<ParsedQuadItem[]>(() => {
        const list: ParsedQuadItem[] = [];
        for (const [tId, state] of targets.entries()) {
            for (const q of state.quads) {
                list.push({ ...q, targetId: tId });
            }
        }
        return list;
    }, [targets]);

    const targetIds = useMemo(() => {
        return Array.from(targets.keys());
    }, [targets]);

    // Target options for PartitionSelector
    const targetOptions = useMemo<PartitionOption[]>(() => {
        if (targetIds.length === 0) {
            return [];
        }
        const hasNonDefaultTarget = targetIds.some((id) => id !== 'default');
        // Only hide if there is <= 1 target AND that target is just the default target
        if (targetIds.length <= 1 && !hasNonDefaultTarget) {
            return [];
        }
        const opts: PartitionOption[] = [
            { id: 'all', label: 'All Targets', count: allQuads.length }
        ];
        for (const id of targetIds) {
            const state = targets.get(id);
            opts.push({
                id,
                label: id === 'default' ? 'Default' : id,
                count: state?.quads.length || 0
            });
        }
        return opts;
    }, [targetIds, targets, allQuads.length]);

    // Ensure selectedTargetId is valid
    useEffect(() => {
        if (selectedTargetId !== 'all' && !targets.has(selectedTargetId)) {
            setSelectedTargetId('all');
        }
    }, [selectedTargetId, targets]);

    // Quads filtered by target
    const quadsFilteredByTarget = useMemo(() => {
        if (selectedTargetId === 'all') {
            return allQuads;
        }
        return allQuads.filter((q) => q.targetId === selectedTargetId);
    }, [allQuads, selectedTargetId]);

    // Graph options based on quadsFilteredByTarget
    const graphOptions = useMemo<PartitionOption[]>(() => {
        const counts = new Map<string, number>();
        for (const q of quadsFilteredByTarget) {
            const g = q.graphName || '(default)';
            counts.set(g, (counts.get(g) || 0) + 1);
        }
        if (counts.size === 0) {
            return [];
        }
        const hasNamedGraph = Array.from(counts.keys()).some((g) => g !== '(default)');
        // Only hide if there is <= 1 graph AND that graph is just the default graph
        if (counts.size <= 1 && !hasNamedGraph) {
            return [];
        }
        const opts: PartitionOption[] = [
            { id: 'all', label: 'All Graphs', count: quadsFilteredByTarget.length }
        ];
        for (const [g, count] of counts.entries()) {
            opts.push({
                id: g,
                label: g === '(default)' ? 'Default Graph' : shortenIri(g, prefixes).display,
                count
            });
        }
        return opts;
    }, [quadsFilteredByTarget, prefixes]);

    // Ensure selectedGraphId is valid
    useEffect(() => {
        if (selectedGraphId !== 'all' && !graphOptions.some((opt) => opt.id === selectedGraphId)) {
            setSelectedGraphId('all');
        }
    }, [selectedGraphId, graphOptions]);

    // Final quads filtered by both Target and Graph
    const displayQuads = useMemo(() => {
        if (selectedGraphId === 'all') {
            return quadsFilteredByTarget;
        }
        return quadsFilteredByTarget.filter((q) => q.graphName === selectedGraphId);
    }, [quadsFilteredByTarget, selectedGraphId]);

    // Target metadata for the details subline
    const selectedTargetMeta = useMemo(() => {
        let activeTargetId: string | null = null;
        if (selectedTargetId !== 'all' && selectedTargetId !== 'default') {
            activeTargetId = selectedTargetId;
        } else if (selectedTargetId === 'all' && targetIds.length === 1 && targetIds[0] !== 'default') {
            activeTargetId = targetIds[0];
        }
        if (!activeTargetId) {
            return null;
        }
        const state = targets.get(activeTargetId);
        const lt = state?.logicalTarget;
        return {
            targetName: lt?.targetPath || activeTargetId,
            targetType: lt?.targetType,
            serialization: formatTargetResource(lt?.serialization, prefixes),
            compression: formatTargetResource(lt?.compression, prefixes),
            encoding: formatTargetResource(lt?.encoding, prefixes)
        };
    }, [selectedTargetId, targetIds, targets, prefixes]);

    return {
        selectedTargetId,
        setSelectedTargetId,
        selectedGraphId,
        setSelectedGraphId,
        allQuads,
        targetIds,
        targetOptions,
        quadsFilteredByTarget,
        graphOptions,
        displayQuads,
        selectedTargetMeta
    };
}
