import { DataFactory, Term } from 'n3';
import { OriginLineage, RdfStatement } from '../../generated/types';

export interface ParsedQuadItem {
    id: string;
    targetId?: string;
    subject: Term;
    predicate: Term;
    object: Term;
    graph: Term;
    graphName: string;
    objectType: 'NamedNode' | 'BlankNode' | 'Literal';
    datatypeIri?: string;
    language?: string;
    lineage?: OriginLineage;
    statement?: RdfStatement;
}

function convertTermToN3(term: any): Term {
    if (!term) return DataFactory.defaultGraph();
    if (typeof term.uri === 'string') {
        return DataFactory.namedNode(term.uri);
    }
    if (typeof term.id === 'string') {
        return DataFactory.blankNode(term.id);
    }
    if (typeof term.value === 'string') {
        if (term.language) {
            return DataFactory.literal(term.value, term.language);
        }
        if (term.datatype && term.datatype.uri) {
            return DataFactory.literal(term.value, DataFactory.namedNode(term.datatype.uri));
        }
        return DataFactory.literal(term.value);
    }
    if (term.id && (term.id.uri || term.id.id)) {
        return convertTermToN3(term.id);
    }
    return DataFactory.blankNode('gen');
}

export function convertRdfStatementToParsedQuad(
    stmt: RdfStatement,
    id: string,
    targetId?: string
): ParsedQuadItem {
    const sTerm = convertTermToN3(stmt.subject);
    const pTerm = convertTermToN3(stmt.predicate);
    const oTerm = convertTermToN3(stmt.object);
    const gTerm = stmt.graph ? convertTermToN3(stmt.graph) : DataFactory.defaultGraph();
    const isDefaultGraph = gTerm.termType === 'DefaultGraph' || !gTerm.value;
    const graphName = isDefaultGraph ? '(default)' : gTerm.value;

    let datatypeIri: string | undefined;
    let language: string | undefined;
    if (oTerm.termType === 'Literal') {
        const lit = stmt.object as any;
        datatypeIri = lit?.datatype?.uri || (oTerm as any).datatype?.value;
        language = lit?.language || (oTerm as any).language || undefined;
    }

    return {
        id,
        targetId,
        subject: sTerm,
        predicate: pTerm,
        object: oTerm,
        graph: gTerm,
        graphName,
        objectType: oTerm.termType as any,
        datatypeIri,
        language,
        lineage: stmt.lineage,
        statement: stmt
    };
}

export function convertStatementsToParsedQuads(
    statements: RdfStatement[],
    idPrefix: string = 'quad',
    targetId?: string
): ParsedQuadItem[] {
    if (!statements || !Array.isArray(statements)) {
        return [];
    }
    return statements.map((stmt, idx) => convertRdfStatementToParsedQuad(stmt, `${idPrefix}-${idx}`, targetId));
}
