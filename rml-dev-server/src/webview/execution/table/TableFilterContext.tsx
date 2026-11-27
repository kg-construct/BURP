import { createContext, useContext } from 'react';
import { SortingState } from '@tanstack/react-table';

export interface TableFilterContextValue {
    subjectSearch: string;
    setSubjectSearch: (val: string) => void;
    subjectType: string;
    setSubjectType: (val: string) => void;
    predicateSearch: string;
    setPredicateSearch: (val: string) => void;
    objectSearch: string;
    setObjectSearch: (val: string) => void;
    objectType: string;
    setObjectType: (val: string) => void;
    selectedDatatype: string;
    setSelectedDatatype: (val: string) => void;
    selectedLanguage: string;
    setSelectedLanguage: (val: string) => void;
    graphSearch: string;
    setGraphSearch: (val: string) => void;
    targetSearch: string;
    setTargetSearch: (val: string) => void;
    availableDatatypes: string[];
    availableLanguages: string[];
    prefixMap?: Record<string, string>;
    sorting: SortingState;
    toggleSorting: (columnId: string) => void;
}

export const TableFilterContext = createContext<TableFilterContextValue | null>(null);

export function useTableFilter(): TableFilterContextValue {
    const ctx = useContext(TableFilterContext);
    if (!ctx) {
        throw new Error('useTableFilter must be used within TableFilterContext.Provider');
    }
    return ctx;
}
