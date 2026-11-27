import { useState } from 'react';
import { useExecutionProtocol } from './useExecutionProtocol';
import { useExecutionPartitions } from './useExecutionPartitions';

export interface UseExecutionOptions {
    isRdfVisualizer?: boolean;
}

/**
 * Composite hook that powers ExecutionApp by uniting the execution protocol lifecycle,
 * multi-target and graph partition filtering, and UI view switching.
 */
export function useExecution(_options?: UseExecutionOptions) {
    const [viewMode, setViewMode] = useState<'table' | 'graph'>('table');

    const protocol = useExecutionProtocol();
    const partitions = useExecutionPartitions({
        targets: protocol.targets,
        prefixes: protocol.prefixes
    });

    return {
        viewMode,
        setViewMode,
        ...protocol,
        ...partitions
    };
}
