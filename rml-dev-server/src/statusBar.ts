import * as vscode from 'vscode';

export type ServerStatus = 'disconnected' | 'starting' | 'connected' | 'error';

export class StatusBarManager {
    private statusBarItem: vscode.StatusBarItem;

    constructor() {
        this.statusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);
        this.statusBarItem.command = 'rml-dev-server.healthCheck';
        this.updateStatus('disconnected');
        this.statusBarItem.show();
    }

    public updateStatus(status: ServerStatus, port?: number, latencyMs?: number): void {
        switch (status) {
            case 'starting':
                this.statusBarItem.text = '$(sync~spin) RML: Starting...';
                this.statusBarItem.tooltip = 'BURP Dev Server is starting... Click for options.';
                this.statusBarItem.backgroundColor = undefined;
                break;
            case 'connected':
                const latencyText = latencyMs !== undefined ? ` (${latencyMs}ms)` : '';
                this.statusBarItem.text = `$(pass-filled) RML: Connected:${port}${latencyText}`;
                this.statusBarItem.tooltip = `BURP Dev Server is connected on port ${port}. Click to run health check.`;
                this.statusBarItem.backgroundColor = undefined;
                break;
            case 'error':
                this.statusBarItem.text = '$(error) RML: Error';
                this.statusBarItem.tooltip = 'BURP Dev Server encountered an error. Click to troubleshoot.';
                this.statusBarItem.backgroundColor = new vscode.ThemeColor('statusBarItem.errorBackground');
                break;
            case 'disconnected':
            default:
                this.statusBarItem.text = '$(circle-slash) RML: Disconnected';
                this.statusBarItem.tooltip = 'BURP Dev Server is disconnected. Click to start / check health.';
                this.statusBarItem.backgroundColor = undefined;
                break;
        }
    }

    public dispose(): void {
        this.statusBarItem.dispose();
    }
}
