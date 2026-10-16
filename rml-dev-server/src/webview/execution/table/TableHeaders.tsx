import React from 'react';
import { useTableFilter } from './TableFilterContext';
import { shortenIri } from '../rdfUtils';

export const SubjectHeader: React.FC = () => {
    const {
        subjectSearch,
        setSubjectSearch,
        subjectType,
        setSubjectType,
        sorting,
        toggleSorting
    } = useTableFilter();

    const sort = sorting.find((s) => s.id === 'subject');

    return (
        <div className="bk-col-header-container">
            <div
                className="bk-col-header-title"
                onClick={(e) => {
                    e.stopPropagation();
                    toggleSorting('subject');
                }}
            >
                <span>Subject</span>
                {sort && (
                    <span className="bk-sort-caret">{sort.desc ? ' ▼' : ' ▲'}</span>
                )}
            </div>
            <div
                className="bk-col-header-controls"
                onClick={(e) => e.stopPropagation()}
                onMouseDown={(e) => e.stopPropagation()}
            >
                <input
                    type="text"
                    className="bk-col-search-input"
                    placeholder="Search..."
                    value={subjectSearch}
                    onChange={(e) => setSubjectSearch(e.target.value)}
                />
                <button
                    type="button"
                    className={`bk-col-filter-btn ${subjectType === 'NamedNode' ? 'active' : ''}`}
                    onClick={() => setSubjectType(subjectType === 'NamedNode' ? 'all' : 'NamedNode')}
                    title="Filter Named Nodes (IRIs)"
                >
                    &lt;&gt;
                </button>
                <button
                    type="button"
                    className={`bk-col-filter-btn ${subjectType === 'BlankNode' ? 'active' : ''}`}
                    onClick={() => setSubjectType(subjectType === 'BlankNode' ? 'all' : 'BlankNode')}
                    title="Filter Blank Nodes"
                >
                    _:
                </button>
            </div>
        </div>
    );
};

export const PredicateHeader: React.FC = () => {
    const { predicateSearch, setPredicateSearch, sorting, toggleSorting } = useTableFilter();
    const sort = sorting.find((s) => s.id === 'predicate');

    return (
        <div className="bk-col-header-container">
            <div
                className="bk-col-header-title"
                onClick={(e) => {
                    e.stopPropagation();
                    toggleSorting('predicate');
                }}
            >
                <span>Predicate</span>
                {sort && (
                    <span className="bk-sort-caret">{sort.desc ? ' ▼' : ' ▲'}</span>
                )}
            </div>
            <div
                className="bk-col-header-controls"
                onClick={(e) => e.stopPropagation()}
                onMouseDown={(e) => e.stopPropagation()}
            >
                <input
                    type="text"
                    className="bk-col-search-input"
                    placeholder="Search..."
                    value={predicateSearch}
                    onChange={(e) => setPredicateSearch(e.target.value)}
                />
            </div>
        </div>
    );
};

export const ObjectHeader: React.FC = () => {
    const {
        objectSearch,
        setObjectSearch,
        objectType,
        setObjectType,
        selectedDatatype,
        setSelectedDatatype,
        selectedLanguage,
        setSelectedLanguage,
        availableDatatypes,
        availableLanguages,
        prefixMap,
        sorting,
        toggleSorting
    } = useTableFilter();

    const sort = sorting.find((s) => s.id === 'object');

    return (
        <div className="bk-col-header-container">
            <div
                className="bk-col-header-title"
                onClick={(e) => {
                    e.stopPropagation();
                    toggleSorting('object');
                }}
            >
                <span>Object</span>
                {sort && (
                    <span className="bk-sort-caret">{sort.desc ? ' ▼' : ' ▲'}</span>
                )}
            </div>
            <div
                className="bk-col-header-controls"
                onClick={(e) => e.stopPropagation()}
                onMouseDown={(e) => e.stopPropagation()}
            >
                <input
                    type="text"
                    className="bk-col-search-input"
                    placeholder="Search..."
                    value={objectSearch}
                    onChange={(e) => setObjectSearch(e.target.value)}
                />
                <button
                    type="button"
                    className={`bk-col-filter-btn ${objectType === 'NamedNode' ? 'active' : ''}`}
                    onClick={() => setObjectType(objectType === 'NamedNode' ? 'all' : 'NamedNode')}
                    title="Filter Named Nodes"
                >
                    &lt;&gt;
                </button>
                <button
                    type="button"
                    className={`bk-col-filter-btn ${objectType === 'BlankNode' ? 'active' : ''}`}
                    onClick={() => setObjectType(objectType === 'BlankNode' ? 'all' : 'BlankNode')}
                    title="Filter Blank Nodes"
                >
                    _:
                </button>
                <button
                    type="button"
                    className={`bk-col-filter-btn ${objectType === 'Literal' ? 'active' : ''}`}
                    onClick={() => setObjectType(objectType === 'Literal' ? 'all' : 'Literal')}
                    title="Filter Literals"
                >
                    “”
                </button>
            </div>
            {objectType === 'Literal' && (
                <div
                    className="bk-literal-subfilters"
                    onClick={(e) => e.stopPropagation()}
                    onMouseDown={(e) => e.stopPropagation()}
                >
                    {availableDatatypes.length > 0 && (
                        <select
                            className="bk-literal-subfilter-select"
                            value={selectedDatatype}
                            onChange={(e) => setSelectedDatatype(e.target.value)}
                            title="Filter by datatype"
                        >
                            <option value="all">All Datatypes</option>
                            {availableDatatypes.map((dt) => (
                                <option key={dt} value={dt}>
                                    {shortenIri(dt, prefixMap).display}
                                </option>
                            ))}
                        </select>
                    )}
                    {availableLanguages.length > 0 && (
                        <select
                            className="bk-literal-subfilter-select"
                            value={selectedLanguage}
                            onChange={(e) => setSelectedLanguage(e.target.value)}
                            title="Filter by language"
                        >
                            <option value="all">All Langs</option>
                            {availableLanguages.map((lang) => (
                                <option key={lang} value={lang}>
                                    @{lang}
                                </option>
                            ))}
                        </select>
                    )}
                </div>
            )}
        </div>
    );
};

export const GraphHeader: React.FC = () => {
    const { graphSearch, setGraphSearch, sorting, toggleSorting } = useTableFilter();
    const sort = sorting.find((s) => s.id === 'graph');

    return (
        <div className="bk-col-header-container">
            <div
                className="bk-col-header-title"
                onClick={(e) => {
                    e.stopPropagation();
                    toggleSorting('graph');
                }}
            >
                <span>Graph</span>
                {sort && (
                    <span className="bk-sort-caret">{sort.desc ? ' ▼' : ' ▲'}</span>
                )}
            </div>
            <div
                className="bk-col-header-controls"
                onClick={(e) => e.stopPropagation()}
                onMouseDown={(e) => e.stopPropagation()}
            >
                <input
                    type="text"
                    className="bk-col-search-input"
                    placeholder="Search..."
                    value={graphSearch}
                    onChange={(e) => setGraphSearch(e.target.value)}
                />
            </div>
        </div>
    );
};

export const TargetHeader: React.FC = () => {
    const { targetSearch, setTargetSearch, sorting, toggleSorting } = useTableFilter();
    const sort = sorting.find((s) => s.id === 'target');

    return (
        <div className="bk-col-header-container">
            <div
                className="bk-col-header-title"
                onClick={(e) => {
                    e.stopPropagation();
                    toggleSorting('target');
                }}
            >
                <span>Target</span>
                {sort && (
                    <span className="bk-sort-caret">{sort.desc ? ' ▼' : ' ▲'}</span>
                )}
            </div>
            <div
                className="bk-col-header-controls"
                onClick={(e) => e.stopPropagation()}
                onMouseDown={(e) => e.stopPropagation()}
            >
                <input
                    type="text"
                    className="bk-col-search-input"
                    placeholder="Search..."
                    value={targetSearch}
                    onChange={(e) => setTargetSearch(e.target.value)}
                />
            </div>
        </div>
    );
};
