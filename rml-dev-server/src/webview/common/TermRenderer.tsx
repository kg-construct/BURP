import React from 'react';
import {Tag} from 'baukasten-ui/core';
import {Term} from 'n3';
import {BNodeValue, IriValue, LiteralValue} from './RdfToken';

// @ts-ignore
import './TermRenderer.css';

interface TermRendererProps {
    term: Term;
    isPredicate?: boolean;
    isObject?: boolean;
    datatype?: string;
    language?: string;
    prefixMap?: Record<string, string>;
}

export const TermRenderer: React.FC<TermRendererProps> = ({
    term,
    datatype,
    language,
    prefixMap
}) => {
    if (!term) {
        return <span className="bk-term-empty">-</span>;
    }

    return (
        <div className="bk-term-cell">
            {term.termType === 'NamedNode' && (
                <IriValue uri={term.value} prefixMap={prefixMap} brackets />
            )}
            {term.termType === 'BlankNode' && (
                <BNodeValue id={term.value} />
            )}
            {term.termType === 'Literal' && (
                <LiteralValue
                    literal={{
                        value: term.value,
                        language,
                        datatype: datatype ? { uri: datatype } : undefined
                    }}
                    prefixMap={prefixMap}
                />
            )}
            {term.termType !== 'NamedNode' && term.termType !== 'BlankNode' && term.termType !== 'Literal' && (
                <span>{term.value}</span>
            )}
        </div>
    );
};

export const GraphRenderer: React.FC<{
    graphName: string;
    prefixMap?: Record<string, string>;
}> = ({ graphName, prefixMap }) => (
    <div className="bk-term-cell">
        {!graphName || graphName === '(default)' ? (
            <Tag size="xs" variant="default">default</Tag>
        ) : (
            <IriValue uri={graphName} prefixMap={prefixMap} brackets />
        )}
    </div>
);
