import {vscodeApi} from './vscode';
import type {EventName, EventProperties} from '../../generated/posthog-typed';
import type {TelemetryFromWebviewMessage} from '../protocol/telemetry';

export type {EventName as TelemetryEventName, EventProperties as TelemetryEventProperties, TelemetryFromWebviewMessage};

export type ExecutionTelemetryEvent =
    | 'execution_view_mode_changed'
    | 'execution_group_by_changed'
    | 'execution_filter_applied'
    | 'execution_scroll_loaded_more'
    | 'execution_partition_changed'
    | 'execution_rerun_clicked';

export const webviewTelemetry = {
    capture<K extends EventName>(event: K, properties?: EventProperties<K>): void {
        try {
            vscodeApi.postMessage({
                command: 'telemetry',
                event,
                properties
            } as TelemetryFromWebviewMessage);
        } catch (err) {
            console.error('[WebviewTelemetry] Error sending telemetry message:', err);
        }
    }
};

