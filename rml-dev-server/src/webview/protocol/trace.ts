import {OriginLineage, Range, TraceResult} from "../../generated/types";
import {TelemetryFromWebviewMessage} from "./telemetry";

export interface TraceQuadTarget {
    subject: string;
    predicate: string;
    object: string;
    graph?: string;
    lineage?: OriginLineage;
}

export type TraceToWebviewMessage =
    | { command: 'traceLoading'; }
    | { command: 'traceError'; error: string; }
    | {
    command: 'traceData';
    result: TraceResult;
    mode: 'iteration' | 'result';
    iterationIndex?: number;
    targetQuad?: TraceQuadTarget;
    title?: string;
};

export type TraceFromWebviewMessage =
    | { command: 'ready'; }
    | { command: 'refresh'; }
    | { command: 'traceIteration'; iterationIndex: number; }
    | { command: 'traceQuad'; quad: TraceQuadTarget; }
    | { command: 'selectRange'; range: Range; }
    | TelemetryFromWebviewMessage;