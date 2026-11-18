import React from 'react';
import {Tag} from 'baukasten-ui/core';
import {shortenIri} from '../execution/rdfUtils';
import {
    BlankNodeOrIRI,
    BlankNodeTerm,
    CollectionOrContainerTerm,
    IRITerm,
    LiteralTerm,
    LogicalTarget,
    RawValue,
    RdfStatement,
    RmlEvaluationResult,
    Term,
} from '../../generated/types';

// @ts-ignore
import './RdfToken.css';

export interface RdfStatementSubjectGraph extends RmlEvaluationResult {
    type?: 'statementSubjectGraph';
    subject?: BlankNodeOrIRI;
    graph?: IRITerm;
}

export interface RdfPredicateObject extends RmlEvaluationResult {
    type?: 'predicateObject';
    predicate?: IRITerm;
    object?: Term;
    graph?: IRITerm;
    targets?: LogicalTarget[];
}

export type TypedStatement = RdfStatement & { type?: 'statement' };
export type TypedLiteral = LiteralTerm & { type?: 'literal' };
export type TypedIri = IRITerm & { type?: 'uri' };
export type TypedBNode = BlankNodeTerm & { type?: 'bnode' };
export type TypedRaw = RawValue & { type?: 'raw' };
export type TypedCollection = CollectionOrContainerTerm & { type?: 'list' | 'alt' | 'bag' | 'seq' };

export type TypedRmlEvaluationResult =
    | TypedStatement
    | TypedLiteral
    | TypedIri
    | TypedBNode
    | TypedRaw
    | TypedCollection
    | RdfStatementSubjectGraph
    | RdfPredicateObject
    | { type: 'raw'; value: unknown };

export interface RdfTokenProps {
    rdf: TypedRmlEvaluationResult | RmlEvaluationResult | null | undefined;
    prefixMap?: Record<string, string>;
    wrapDatatypeNewline?: boolean;
}

export function isIriTerm(term: unknown): term is IRITerm & { type?: 'uri' } {
    return typeof term === 'object' && term !== null && 'uri' in term && typeof (term as {
        uri: unknown
    }).uri === 'string';
}

export function isBlankNodeTerm(term: unknown): term is BlankNodeTerm & { type?: 'bnode' } {
    return typeof term === 'object' && term !== null && 'id' in term && typeof (term as {
        id: unknown
    }).id === 'string';
}

export function isLiteralTerm(term: unknown): term is LiteralTerm & { type?: 'literal' } {
    return typeof term === 'object' && term !== null && 'value' in term && (term as {
        value: unknown
    }).value !== undefined;
}

export function isCollectionOrContainerTerm(term: unknown): term is TypedCollection {
    if (typeof term !== 'object' || term === null) return false;
    const obj = term as Record<string, unknown>;
    return (
        obj.type === 'list' ||
        obj.type === 'bag' ||
        obj.type === 'seq' ||
        obj.type === 'alt' ||
        (Array.isArray(obj.elements) && 'idGenerated' in obj)
    );
}

export function isStatement(item: unknown): item is TypedStatement {
    if (typeof item !== 'object' || item === null) return false;
    const obj = item as Record<string, unknown>;
    return obj.type === 'statement' || (obj.subject !== undefined && obj.predicate !== undefined && obj.object !== undefined);
}

export function isStatementSubjectGraph(item: unknown): item is RdfStatementSubjectGraph {
    if (typeof item !== 'object' || item === null) return false;
    const obj = item as Record<string, unknown>;
    return obj.type === 'statementSubjectGraph' || (obj.subject !== undefined && obj.graph !== undefined && obj.predicate === undefined);
}

export function isPredicateObject(item: unknown): item is RdfPredicateObject {
    if (typeof item !== 'object' || item === null) return false;
    const obj = item as Record<string, unknown>;
    return obj.type === 'predicateObject' || (obj.predicate !== undefined && obj.object !== undefined && obj.subject === undefined);
}

export function isRawValue(item: unknown): item is (RawValue & { type?: 'raw' }) | { type: 'raw'; value: unknown } {
    if (typeof item !== 'object' || item === null) return false;
    return (item as Record<string, unknown>).type === 'raw';
}

export const IriValue: React.FC<{
    uri: string;
    prefixMap?: Record<string, string>;
    brackets?: boolean;
}> = ({uri, prefixMap, brackets}) => {
    const s = shortenIri(uri, prefixMap);
    if (s.isCurie) {
        const colonIdx = s.display.indexOf(':');
        if (colonIdx !== -1) {
            const prefix = s.display.slice(0, colonIdx + 1);
            const local = s.display.slice(colonIdx + 1);
            return (
                <span className="bk-uri-text" title={uri}>
                    <span className="bk-curie-prefix">{prefix}</span>
                    <span className="bk-curie-local">{local}</span>
                </span>
            );
        }
        return <span className="bk-uri-text" title={uri}>{s.display}</span>;
    }

    if (brackets) {
        return (
            <span className="bk-uri-text" title={uri}>
                <span className="bk-term-bracket">&lt;</span>
                {uri}
                <span className="bk-term-bracket">&gt;</span>
            </span>
        );
    }

    return <span className="bk-uri-text" title={uri}>{s.display}</span>;
};

export const BNodeValue: React.FC<{
    id: string;
}> = ({id}) => (
    <span className="bk-bnode-wrapper" title={`Blank Node: _:${id}`}>
        <span className="bk-term-bracket">_:</span>
        <span className="bk-bnode-text">{id}</span>
    </span>
);

export const LiteralValue: React.FC<{
    literal: TypedLiteral | { value?: string | number | boolean; datatype?: { uri?: string }; language?: string };
    prefixMap?: Record<string, string>;
    wrapDatatypeNewline?: boolean;
}> = ({literal, prefixMap, wrapDatatypeNewline}) => {
    const val = literal.value !== undefined ? String(literal.value) : '';
    const dtUri = literal.datatype?.uri;
    const dtShort = dtUri ? shortenIri(dtUri, prefixMap).display : '';
    const dt = dtShort ? `^^${dtShort}` : '';
    const lang = literal.language ? `@${literal.language}` : '';
    const isXsdString = dtUri === 'http://www.w3.org/2001/XMLSchema#string';

    const content = (
        <>
            <span className="bk-literal-quoted">
                <span className="bk-term-quote">"</span>
                <span className="bk-literal-text">{val}</span>
                <span className="bk-term-quote">"</span>
            </span>
            {lang && (
                <span className="bk-chip-lang" title={`Language: ${literal.language}`}>
                    {lang}
                </span>
            )}
            {dt && (
                <span
                    className={`bk-chip-dt ${isXsdString ? 'bk-chip-dt-muted' : ''}`}
                    title={`Datatype: ${dtUri || dtShort}`}
                >
                    {dt}
                </span>
            )}
        </>
    );

    if (wrapDatatypeNewline && dt) {
        return (
            <div className="bk-obj-wrapper">
                <div>
                    <span className="bk-literal-quoted">
                        <span className="bk-term-quote">"</span>
                        <span className="bk-literal-text">{val}</span>
                        <span className="bk-term-quote">"</span>
                    </span>
                    {lang && (
                        <span className="bk-chip-lang" title={`Language: ${literal.language}`}>
                            {lang}
                        </span>
                    )}
                </div>
                <div className="bk-dt-newline">
                    <span
                        className={`bk-chip-dt ${isXsdString ? 'bk-chip-dt-muted' : ''}`}
                        title={`Datatype: ${dtUri || dtShort}`}
                    >
                        {dt}
                    </span>
                </div>
            </div>
        );
    }

    return content;
};

export const CollectionValue: React.FC<{
    collection: TypedCollection;
    prefixMap?: Record<string, string>;
    wrapDatatypeNewline?: boolean;
}> = ({collection, prefixMap, wrapDatatypeNewline}) => {
    const collType = collection.type ?? 'list';
    const isList = collType === 'list';
    const openBrace = isList ? '(' : `${collType.charAt(0).toUpperCase() + collType.slice(1)}[`;
    const closeBrace = isList ? ')' : ']';
    const elements = collection.elements ?? [];

    return (
        <span className="bk-collection-wrapper">
            {!collection.idGenerated && collection.id && (
                <span className="bk-collection-id">
                    <TermValue term={collection.id} prefixMap={prefixMap} wrapDatatypeNewline={wrapDatatypeNewline}/>
                </span>
            )}
            <span className="bk-collection-bracket">{openBrace}</span>
            {elements.map((elem, idx) => (
                <span key={idx} className="bk-collection-item">
                    <TermValue term={elem} prefixMap={prefixMap} wrapDatatypeNewline={wrapDatatypeNewline}/>
                    {!isList && idx < elements.length - 1 && <span className="bk-collection-bracket">,</span>}
                </span>
            ))}
            <span className="bk-collection-bracket">{closeBrace}</span>
        </span>
    );
};

export const TermValue: React.FC<{
    term: unknown;
    prefixMap?: Record<string, string>;
    wrapDatatypeNewline?: boolean;
}> = ({term, prefixMap, wrapDatatypeNewline}) => {
    if (!term) return null;
    if (isIriTerm(term)) {
        return <IriValue uri={term.uri} prefixMap={prefixMap}/>;
    }
    if (isBlankNodeTerm(term)) {
        return <BNodeValue id={term.id}/>;
    }
    if (isLiteralTerm(term)) {
        return <LiteralValue literal={term} prefixMap={prefixMap} wrapDatatypeNewline={wrapDatatypeNewline}/>;
    }
    if (isCollectionOrContainerTerm(term)) {
        return <CollectionValue collection={term} prefixMap={prefixMap} wrapDatatypeNewline={wrapDatatypeNewline}/>;
    }
    if (typeof term === 'object' && term !== null && 'value' in term) {
        return <span className="bk-literal-text">{String((term as { value: unknown }).value ?? '')}</span>;
    }
    return <span className="bk-literal-text">{String(term)}</span>;
};

export const RdfToken: React.FC<RdfTokenProps> = ({rdf, prefixMap, wrapDatatypeNewline}) => {
    if (!rdf) {
        return null;
    }

    if (isStatement(rdf)) {
        return <StatementToken statement={rdf} prefixMap={prefixMap} wrapDatatypeNewline={wrapDatatypeNewline}/>;
    }

    if (isStatementSubjectGraph(rdf)) {
        return <StatementSubjectGraphToken statement={rdf} prefixMap={prefixMap}/>;
    }

    if (isPredicateObject(rdf)) {
        return <PredicateObjectToken item={rdf} prefixMap={prefixMap} wrapDatatypeNewline={wrapDatatypeNewline}/>;
    }

    if (isRawValue(rdf)) {
        return <RawToken value={rdf.value}/>;
    }

    if (isIriTerm(rdf)) {
        return <IriToken uri={rdf.uri} prefixMap={prefixMap}/>;
    }

    if (isLiteralTerm(rdf)) {
        return <LiteralToken literal={rdf} prefixMap={prefixMap} wrapDatatypeNewline={wrapDatatypeNewline}/>;
    }

    if (isBlankNodeTerm(rdf)) {
        return <BNodeToken id={rdf.id}/>;
    }

    if (isCollectionOrContainerTerm(rdf)) {
        return <CollectionToken collection={rdf} prefixMap={prefixMap} wrapDatatypeNewline={wrapDatatypeNewline}/>;
    }

    return <pre className="bk-code-box">{JSON.stringify(rdf, null, 2)}</pre>;
};

export const RawToken: React.FC<{ value: unknown }> = ({value}) => (
    <div className="bk-statement-token">
        <div className="bk-token-row">
            <Tag size="xs" variant="default">RAW</Tag>
            <span
                className="bk-literal-text">{typeof value === 'object' ? JSON.stringify(value) : String(value ?? '')}</span>
        </div>
    </div>
);

export const SubjectRow: React.FC<{
    subject?: BlankNodeOrIRI;
    prefixMap?: Record<string, string>;
}> = ({subject, prefixMap}) => (
    <div className="bk-token-row">
        <Tag size="xs" variant="info">SUBJ</Tag>
        <TermValue term={subject} prefixMap={prefixMap}/>
    </div>
);

export const GraphRow: React.FC<{
    graph?: IRITerm;
    prefixMap?: Record<string, string>;
}> = ({graph, prefixMap}) => {
    if (!graph?.uri) {
        return null;
    }
    return (
        <div className="bk-token-row">
            <Tag size="xs" variant="warning">GRAPH</Tag>
            <IriValue uri={graph.uri} prefixMap={prefixMap}/>
        </div>
    );
};

export const StatementSubjectGraphToken: React.FC<{
    statement: RdfStatementSubjectGraph;
    prefixMap?: Record<string, string>;
}> = ({statement, prefixMap}) => (
    <div className="bk-statement-token">
        <SubjectRow subject={statement.subject} prefixMap={prefixMap}/>
        <GraphRow graph={statement.graph} prefixMap={prefixMap}/>
    </div>
);

export const PredicateObjectToken: React.FC<{
    item: RdfPredicateObject;
    prefixMap?: Record<string, string>;
    wrapDatatypeNewline?: boolean;
}> = ({item, prefixMap, wrapDatatypeNewline}) => {
    const pUri = item.predicate?.uri;

    return (
        <div className="bk-statement-token">
            <div className="bk-token-row">
                <Tag size="xs" variant="secondary">PRED</Tag>
                {pUri ? (
                    <IriValue uri={pUri} prefixMap={prefixMap}/>
                ) : (
                    <span className="bk-pred-text">{item.predicate ? String(item.predicate) : ''}</span>
                )}
            </div>
            <div className="bk-token-row bk-token-row-obj">
                <Tag size="xs" variant="success">OBJ</Tag>
                <TermValue
                    term={item.object}
                    prefixMap={prefixMap}
                    wrapDatatypeNewline={wrapDatatypeNewline}
                />
            </div>
            <GraphRow graph={item.graph} prefixMap={prefixMap}/>
        </div>
    );
};

export const StatementToken: React.FC<{
    statement: TypedStatement;
    prefixMap?: Record<string, string>;
    wrapDatatypeNewline?: boolean;
}> = ({statement, prefixMap, wrapDatatypeNewline}) => {
    const pUri = statement.predicate?.uri;

    return (
        <div className="bk-statement-token">
            <SubjectRow subject={statement.subject} prefixMap={prefixMap}/>
            <div className="bk-token-row">
                <Tag size="xs" variant="secondary">PRED</Tag>
                {pUri ? (
                    <IriValue uri={pUri} prefixMap={prefixMap}/>
                ) : (
                    <span className="bk-pred-text">{statement.predicate ? String(statement.predicate) : ''}</span>
                )}
            </div>
            <div className="bk-token-row bk-token-row-obj">
                <Tag size="xs" variant="success">OBJ</Tag>
                <TermValue
                    term={statement.object}
                    prefixMap={prefixMap}
                    wrapDatatypeNewline={wrapDatatypeNewline}
                />
            </div>
            <GraphRow graph={statement.graph} prefixMap={prefixMap}/>
        </div>
    );
};

export const IriToken: React.FC<{ uri: string; prefixMap?: Record<string, string> }> = ({uri, prefixMap}) => (
    <div className="bk-statement-token">
        <div className="bk-token-row">
            <Tag size="xs" variant="info">IRI</Tag>
            <IriValue uri={uri} prefixMap={prefixMap}/>
        </div>
    </div>
);

export const LiteralToken: React.FC<{
    literal: TypedLiteral | { value?: string | number | boolean; datatype?: { uri?: string }; language?: string };
    prefixMap?: Record<string, string>;
    wrapDatatypeNewline?: boolean;
}> = ({literal, prefixMap, wrapDatatypeNewline}) => (
    <div className="bk-statement-token">
        <div className="bk-token-row">
            <Tag size="xs" variant="success">LITERAL</Tag>
            <LiteralValue literal={literal} prefixMap={prefixMap} wrapDatatypeNewline={wrapDatatypeNewline}/>
        </div>
    </div>
);

export const BNodeToken: React.FC<{ id: string }> = ({id}) => (
    <div className="bk-statement-token">
        <div className="bk-token-row">
            <Tag size="xs" variant="warning">BNODE</Tag>
            <BNodeValue id={id}/>
        </div>
    </div>
);

export const CollectionToken: React.FC<{
    collection: TypedCollection;
    prefixMap?: Record<string, string>;
    wrapDatatypeNewline?: boolean;
}> = ({collection, prefixMap, wrapDatatypeNewline}) => {
    const collType = (collection.type ?? 'list').toUpperCase();
    return (
        <div className="bk-statement-token">
            <div className="bk-token-row">
                <Tag size="xs" variant="primary">{collType}</Tag>
                <CollectionValue collection={collection} prefixMap={prefixMap}
                                 wrapDatatypeNewline={wrapDatatypeNewline}/>
            </div>
        </div>
    );
};
