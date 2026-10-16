import {ExecuteResult, QuadChunkNotification} from "../../generated/types";
import {TraceQuadTarget} from "./trace";
import {ExecutionSessionState} from "../../ui/executionManager";
import {TelemetryFromWebviewMessage} from "./telemetry";

export type ExecutionToWebviewMessage =
    | {
    type: 'startExecution';
    fileName?: string;
    graphBackgroundMethod?: any;
    prefixes?: Record<string, string>;
}
    | { type: 'quadChunk'; chunk: QuadChunkNotification; }
    | { type: 'executionComplete'; result: ExecuteResult; }
    | { type: 'executionError'; error: string; }
    | { type: 'sessionState'; session: ExecutionSessionState; }
    ;


export type ExecutionFromWebviewMessage =
    | { command: 'ready'; }
    | { command: 'rerun'; }
    | { command: 'clearCacheAndRerun'; }
    | { command: 'traceIteration'; iterationIndex: number; triplesMapIri?: string; }
    | { command: 'traceQuad'; quad: TraceQuadTarget; }
    | TelemetryFromWebviewMessage
    ;