import * as crypto from 'crypto';
import * as vscode from 'vscode';
import posthog, {EventName, EventProperties} from './generated/posthog-typed';
import {getConfiguration} from './config';

export type {EventName, EventProperties};

const MAX_TELEMETRY_TEXT_LENGTH = 256;
const MAX_STACK_LINES = 20;

const URI_RE = /(?:file:\/+|https?:\/\/|jdbc:)[^\s'"()<>[\]]+/gi;
const EMAIL_RE = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g;
const KNOWN_UNIX_PATH_RE = /(?<![A-Za-z0-9._-])\/(?:home|Users|root|tmp|var|etc|mnt|opt|srv|Volumes)\/[^\s:'"(),<>[\]]+/g;
const GENERAL_UNIX_PATH_RE = /(?<![A-Za-z0-9._-])\/[A-Za-z0-9._-]+\/[A-Za-z0-9._/-]+/g;
const WINDOWS_PATH_RE = /(?<![A-Za-z0-9_])[A-Za-z]:[\\/][^\s:'"(),<>[\]]+/g;
const QUOTED_LITERAL_RE = /"([^"\n]{4,})"|(?<![A-Za-z0-9])'([^'\n]{4,})'(?![A-Za-z0-9])/g;

function truncateTelemetryText(text: string): string {
    if (text.length <= MAX_TELEMETRY_TEXT_LENGTH) {
        return text;
    }
    return text.slice(0, MAX_TELEMETRY_TEXT_LENGTH - 3) + '...';
}

export function sanitizeTelemetryText(text: string): string {
    const sanitized = text
        .replace(URI_RE, '<redacted-uri>')
        .replace(EMAIL_RE, '<redacted-email>')
        .replace(KNOWN_UNIX_PATH_RE, '<redacted-path>')
        .replace(GENERAL_UNIX_PATH_RE, '<redacted-path>')
        .replace(WINDOWS_PATH_RE, '<redacted-path>')
        .replace(QUOTED_LITERAL_RE, (match, doubleQuoted?: string, singleQuoted?: string) => {
            const inner = doubleQuoted ?? singleQuoted ?? '';
            if (/^<redacted(?:-[a-z]+)?>$/.test(inner)) {
                return match;
            }
            return '"<redacted>"';
        });

    return truncateTelemetryText(sanitized);
}

function stripDirectoryPrefix(location: string): string {
    const locMatch = /^(?<filePath>.+?)(?<lineCol>:\d+(?::\d+)?)?$/.exec(location);
    if (!locMatch?.groups) {
        return sanitizeTelemetryText(location);
    }

    const {filePath, lineCol = ''} = locMatch.groups;
    const lastSep = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
    if (lastSep === -1) {
        return `${filePath}${lineCol}`;
    }

    const basename = filePath.slice(lastSep + 1);
    if (!basename) {
        return sanitizeTelemetryText(location);
    }

    return `${basename}${lineCol}`;
}

function sanitizeStackFrame(line: string): string {
    const parenFrameMatch = /^(?<prefix>\s*at\s+.*?)\s+\((?<loc>[^()]+)\)\s*$/.exec(line);
    if (parenFrameMatch?.groups) {
        const {prefix, loc} = parenFrameMatch.groups;
        return sanitizeTelemetryText(`${prefix} (${stripDirectoryPrefix(loc)})`);
    }

    if (!line.includes('(')) {
        const bareFrameMatch = /^(?<prefix>\s*at\s+(?:async\s+)?)(?<loc>.+?)\s*$/.exec(line);
        if (bareFrameMatch?.groups) {
            const {prefix, loc} = bareFrameMatch.groups;
            return sanitizeTelemetryText(`${prefix}${stripDirectoryPrefix(loc)}`);
        }
    }

    return sanitizeTelemetryText(line);
}

export function sanitizeStackTrace(stack?: string): string | undefined {
    if (!stack) {
        return undefined;
    }

    const lines = stack.split(/\r?\n/).slice(0, MAX_STACK_LINES);
    return lines
        .map((line, index) => {
            if (index === 0) {
                return sanitizeTelemetryText(line);
            }
            if (/^\s*at\s+/.test(line)) {
                return sanitizeStackFrame(line);
            }
            return sanitizeTelemetryText(line);
        })
        .join('\n');
}

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
    private appVersion = 'unknown';

    constructor() {
        this.isTestMode = process.env.VSCODE_TEST !== undefined || process.env.NODE_ENV === 'test';
        this.sessionId = crypto.randomUUID();
    }

    public initialize(context: vscode.ExtensionContext): void {
        this.extensionContext = context;
        this.appVersion =
            (context.extension?.packageJSON as {version?: string} | undefined)?.version ?? 'unknown';
        this.isTestMode =
            context.extensionMode === vscode.ExtensionMode.Test ||
            process.env.VSCODE_TEST !== undefined;

        let id = context.globalState.get<string>('rml.telemetry.sessionId');
        if (!id) {
            id = crypto.randomUUID();
            context.globalState.update('rml.telemetry.sessionId', id);
        }
        this.sessionId = id;

        context.subscriptions?.push(
            vscode.workspace.onDidChangeConfiguration((e) => {
                if (e.affectsConfiguration('rml.telemetry.enabled')) {
                    this.syncTelemetryState();
                }
            }),
            vscode.env.onDidChangeTelemetryEnabled(() => {
                this.syncTelemetryState();
            })
        );

        this.syncTelemetryState();
    }

    private ensureInitialized(): void {
        if (this.isInitialized || this.extensionContext === undefined || this.isTestMode || !this.isTelemetryEnabled()) {
            return;
        }
        try {
            posthog.init(this.apiKey, {
                api_host: this.host,
                autocapture: false,
                disable_session_recording: true,
                capture_pageview: false,
                capture_pageleave: false,
                persistence: 'memory',
                bootstrap: {
                    distinctID: this.getDistinctId()
                }
            });
            this.isInitialized = true;
        } catch {
            // Fire-and-forget: ignore any init errors in non-browser environment
        }
    }

    private syncTelemetryState(): void {
        if (this.isTelemetryEnabled()) {
            this.ensureInitialized();
            if (this.isInitialized) {
                try {
                    posthog.opt_in_capturing?.();
                } catch {
                    // Fire-and-forget: ignore opt-in errors in non-browser environment
                }
            }
        } else if (this.isInitialized) {
            try {
                posthog.opt_out_capturing?.();
            } catch {
                // Fire-and-forget: ignore opt-out errors in non-browser environment
            }
        }
    }

    public getDistinctId(): string {
        return vscode.env.machineId || this.sessionId;
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
        this.ensureInitialized();

        const distinctId = this.getDistinctId();
        const sessionId = (vscode.env as any).sessionId || this.sessionId;

        const fullProperties: Record<string, any> = {
            distinct_id: distinctId,
            $session_id: sessionId,
            $device_id: distinctId,
            $app_name: 'rml-dev-server',
            $app_version: this.appVersion,
            $os: process.platform,
            ...(properties || {})
        };

        try {
            posthog.capture(event, fullProperties as any);
        } catch {
            // Fire-and-forget: ignore any capture errors in non-browser environment
        }
    }

    public setStudyPseudo(pseudo: string): void {
        const normalized = pseudo.trim();
        if (!normalized || !this.isTelemetryEnabled()) {
            return;
        }
        this.ensureInitialized();
        try {
            posthog.identify(this.getDistinctId(), {
                study_pseudo: normalized
            });
        } catch {
            // Fire-and-forget: ignore any identify errors in non-browser environment
        }
    }

    public captureException(error: Error | unknown, context?: string): void {
        if (!this.isTelemetryEnabled()) {
            return;
        }

        const errObj = error instanceof Error ? error : new Error(String(error));
        this.capture('$exception' as any, {
            $exception_message: sanitizeTelemetryText(errObj.message),
            $exception_type: errObj.name || 'Error',
            $exception_stack_trace_raw: sanitizeStackTrace(errObj.stack),
            source: 'rml-dev-server',
            handled: true,
            context: context ? sanitizeTelemetryText(context) : undefined
        });
    }
}

export const telemetry = new TelemetryService();
