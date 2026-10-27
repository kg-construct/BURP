import {PlanNodeItem, Range} from "../../generated/types";
import {TelemetryFromWebviewMessage} from "./telemetry";

export type PlanToWebviewMessage =
    | {
    command: 'render';
    roots?: PlanNodeItem[];
    selectedNodeId?: string | null;
    fileUri?: string | null;
    emptyMessage?: string;
};

export type PlanFromWebviewMessage =
    | { command: 'ready'; }
    | { command: 'refresh'; }
    | { command: 'openIssues'; }
    | { command: 'nodeExpanded'; nodeId: string; nodeType: string; }
    | {
    command: 'selectRange';
    fileUri: string;
    range: Range;
    ranges?: Range[];
}
    | TelemetryFromWebviewMessage;