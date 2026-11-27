import type {EventName, EventProperties} from '../../generated/posthog-typed';

export type SpecificTelemetryFromWebviewMessage<K extends EventName = EventName> = {
    command: 'telemetry';
    event: K;
    properties?: EventProperties<K>;
};

export type TelemetryFromWebviewMessage = {
    [K in EventName]: SpecificTelemetryFromWebviewMessage<K>;
}[EventName];
