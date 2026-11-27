import {SourceDataResult} from "../../generated/types";
import {TelemetryFromWebviewMessage} from "./telemetry";

export type SourceToWebviewMessage =
    | { command: 'renderPreview'; data: SourceDataResult; }
    | { command: 'setLoading'; isInitial?: boolean; }
    | { command: 'setError'; error: string; };


export type SourceFromWebviewMessage =
    | { command: 'ready'; }
    | {
    command: 'fetchMore';
    offset: number;
    limit: number;
}
    | {
    command: 'openInEditor';
    content: string;
    language?: string;
}
    | {
    command: 'traceIteration';
    iterationIndex: number;
}
    | TelemetryFromWebviewMessage;