import * as vscode from 'vscode';

export type ServerStatus = 'disconnected' | 'starting' | 'connected' | 'error';

export class StatusBarManager {
    private statusBarItem: vscode.StatusBarItem;
    private currentStatus: ServerStatus = 'disconnected';
    private currentPort?: number;
    private currentLatencyMs?: number;

    constructor() {
        this.statusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);
        this.statusBarItem.command = 'rml.healthCheck';
        this.updateStatus('disconnected');
        this.statusBarItem.show();
    }

    public updateStatus(status: ServerStatus, port?: number, latencyMs?: number): void {
        this.currentStatus = status;
        if (port !== undefined) {
            this.currentPort = port;
        } else if (status === 'disconnected' || status === 'starting') {
            this.currentPort = undefined;
        }

        if (latencyMs !== undefined) {
            this.currentLatencyMs = latencyMs;
        } else if (status === 'disconnected' || status === 'starting') {
            this.currentLatencyMs = undefined;
        }

        const effectivePort = port ?? this.currentPort;
        const effectiveLatency = latencyMs ?? this.currentLatencyMs;

        switch (status) {
            case 'starting':
                this.statusBarItem.text = '$(sync~spin) RML: Starting...';
                this.statusBarItem.tooltip = this.createTooltip('starting');
                this.statusBarItem.backgroundColor = undefined;
                break;
            case 'connected':
                const latencyText = effectiveLatency !== undefined ? ` (${effectiveLatency}ms)` : '';
                this.statusBarItem.text = `$(pass-filled) RML: Connected:${effectivePort ?? ''}${latencyText}`;
                this.statusBarItem.tooltip = this.createTooltip('connected', effectivePort, effectiveLatency);
                this.statusBarItem.backgroundColor = undefined;
                break;
            case 'error':
                this.statusBarItem.text = '$(error) RML: Error';
                this.statusBarItem.tooltip = this.createTooltip('error');
                this.statusBarItem.backgroundColor = new vscode.ThemeColor('statusBarItem.errorBackground');
                break;
            case 'disconnected':
            default:
                this.statusBarItem.text = '$(circle-slash) RML: Disconnected';
                this.statusBarItem.tooltip = this.createTooltip('disconnected');
                this.statusBarItem.backgroundColor = undefined;
                break;
        }
    }

    private createTooltip(status: ServerStatus, port?: number, latencyMs?: number): vscode.MarkdownString {
        const md = new vscode.MarkdownString('', true);
        md.isTrusted = true;
        md.supportThemeIcons = true;
        md.supportHtml = true;

        md.appendMarkdown('### RML Dev Server\n\n');

        switch (status) {
            case 'connected': {
                const latencyText = latencyMs !== undefined ? ` (${latencyMs}ms latency)` : '';
                md.appendMarkdown(`$(pass-filled) **Connected** on port \`${port}\`${latencyText}\n\n`);
                md.appendMarkdown('---\n\n');
                md.appendMarkdown('[$(refresh) Restart](command:rml.restartServer "Restart RML Dev Server") &nbsp;|&nbsp; ');
                md.appendMarkdown('[$(trash) Clear Cache](command:rml.clearCache "Clear RML Execution Cache") &nbsp;|&nbsp; ');
                md.appendMarkdown('[$(gear) Settings](command:rml.openSettings "Open Settings")\n\n');
                md.appendMarkdown('[$(pulse) Health Check](command:rml.healthCheck "Run Health Check") &nbsp;|&nbsp; ');
                md.appendMarkdown('[$(output) Show Output](command:rml.showOutput "Show Output Channel") &nbsp;|&nbsp; ');
                md.appendMarkdown('[$(debug-stop) Stop](command:rml.stopServer "Stop Server")');
                break;
            }
            case 'starting': {
                md.appendMarkdown('$(sync~spin) **Starting Dev Server...**\n\n');
                md.appendMarkdown('---\n\n');
                md.appendMarkdown('[$(output) Show Output](command:rml.showOutput "Show Output Channel") &nbsp;|&nbsp; ');
                md.appendMarkdown('[$(gear) Settings](command:rml.openSettings "Open Settings")');
                break;
            }
            case 'error': {
                md.appendMarkdown('$(error) **Dev Server Error**\n\n');
                md.appendMarkdown('---\n\n');
                md.appendMarkdown('[$(refresh) Retry / Restart](command:rml.restartServer "Restart Server") &nbsp;|&nbsp; ');
                md.appendMarkdown('[$(output) Show Logs](command:rml.showOutput "Show Output Channel") &nbsp;|&nbsp; ');
                md.appendMarkdown('[$(gear) Settings](command:rml.openSettings "Open Settings")');
                break;
            }
            case 'disconnected':
            default: {
                md.appendMarkdown('$(circle-slash) **Disconnected**\n\n');
                md.appendMarkdown('---\n\n');
                md.appendMarkdown('[$(play) Start Server](command:rml.startServer "Start Server") &nbsp;|&nbsp; ');
                md.appendMarkdown('[$(gear) Settings](command:rml.openSettings "Open Settings") &nbsp;|&nbsp; ');
                md.appendMarkdown('[$(output) Show Output](command:rml.showOutput "Show Output Channel")');
                break;
            }
        }

        return md;
    }

    public getTooltip(): vscode.MarkdownString | string | undefined {
        return this.statusBarItem.tooltip;
    }

    public getText(): string {
        return this.statusBarItem.text;
    }

    public dispose(): void {
        this.statusBarItem.dispose();
    }
}
