import {EvaluateResult, ReferenceDependency} from "../../generated/types";
import {TypedDataContext} from "../../dataContext";
import {TelemetryFromWebviewMessage} from "./telemetry";

export type EvaluatorToWebviewMessage =
    | {
    command: 'render';
    results?: EvaluateResult[];
    prefixes?: Record<string, string>;
    cursor?: { line: number; character: number };
    fileUri?: string;
    emptyMessage?: string;
}
    | {
    command: 'renderDependencies';
    dependencies: ReferenceDependency[];
};
export type EvaluatorFromWebviewMessage =
    | { command: 'ready'; }
    | { command: 'refresh'; }
    | { command: 'fetchDependencies'; }
    | { command: 'runEvaluation'; dataContext: TypedDataContext; }
    | TelemetryFromWebviewMessage;