import * as crypto from 'crypto';
import * as vscode from 'vscode';
import posthog, {EventName, EventProperties} from './generated/posthog-typed';
import {getConfiguration} from './config';

export type {EventName, EventProperties};

export function roundToMagnitude(count: number): number {
    if (count <= 0) {
        return 0;
    }
    if (count < 10) {
        return count;
    }
    const magnitude = Math.pow(10, Math.floor(Math.log10(count)));
    return Math.floor(count / magnitude) * magnitude;
}

export class TelemetryService {
    public readonly apiKey: string = 'phc_v4YBtUycMKoZiFPg8qSkuFUa3bTfDsuxq7TgYVZzpLUm';
    public readonly host: string = 'https://eu.i.posthog.com';

    private sessionId: string;
    private isTestMode: boolean;
    private isInitialized = false;
    private extensionContext?: vscode.ExtensionContext;

    constructor() {
        this.isTestMode = process.env.VSCODE_TEST !== undefined || process.env.NODE_ENV === 'test';
        this.sessionId = crypto.randomUUID();
    }

    public initialize(context: vscode.ExtensionContext): void {
        this.extensionContext = context;
        this.isTestMode =
            context.extensionMode === vscode.ExtensionMode.Test ||
            (context as any).extensionMode === 3 ||
            process.env.VSCODE_TEST !== undefined ||
            process.env.NODE_ENV === 'test';

        let id = context.globalState.get<string>('rml.telemetry.sessionId');
        if (!id) {
            id = crypto.randomUUID();
            context.globalState.update('rml.telemetry.sessionId', id);
        }
        this.sessionId = id;

        if (!this.isInitialized && !this.isTestMode) {
            posthog.init(this.apiKey, {
                api_host: this.host,
                autocapture: false,
                disable_session_recording: true,
                capture_pageview: false,
                capture_pageleave: false,
                persistence: 'memory'
            });
            this.isInitialized = true;
        }
    }

    public getSessionId(): string {
        return this.sessionId;
    }

    public setTestMode(testMode: boolean): void {
        this.isTestMode = testMode;
    }

    public isTelemetryEnabled(): boolean {
        if (this.isTestMode) {
            return false;
        }

        try {
            const setting = getConfiguration().telemetryEnabled;
            if (setting === 'enabled') {
                return true;
            }
            if (setting === 'disabled') {
                return false;
            }
        } catch {
            // If configuration fails to resolve, fallback to global VS Code telemetry setting
        }

        return vscode.env.isTelemetryEnabled ?? false;
    }

    public capture<K extends EventName>(
        event: K,
        properties?: EventProperties<K>
    ): void {
        if (!this.isTelemetryEnabled()) {
            return;
        }

        const distinctId = vscode.env.machineId || 'anonymous';
        const sessionId = (vscode.env as any).sessionId || this.sessionId;

        const fullProperties: Record<string, any> = {
            distinct_id: distinctId,
            $session_id: sessionId,
            $device_id: distinctId,
            $app_name: 'rml-dev-server',
            $app_version: '0.0.1',
            $os: process.platform,
            ...(properties || {})
        };

        try {
            posthog.capture(event, fullProperties as any);
        } catch {
            // Fire-and-forget: ignore any capture errors in non-browser environment
        }
    }

    public captureException(error: Error | unknown, context?: string): void {
        if (!this.isTelemetryEnabled()) {
            return;
        }

        const errObj = error instanceof Error ? error : new Error(String(error));
        this.capture('$exception' as any, {
            $exception_message: errObj.message,
            $exception_type: errObj.name || 'Error',
            $exception_stack_trace_raw: errObj.stack,
            source: 'rml-dev-server',
            handled: true,
            context
        });
    }
}

export const telemetry = new TelemetryService();
