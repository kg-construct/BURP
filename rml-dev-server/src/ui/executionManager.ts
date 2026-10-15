import * as path from 'path';
import * as crypto from 'crypto';
import * as vscode from 'vscode';
import {RmlDevClient} from '../client';
import {ExecuteResult} from '../generated/types';
import {getWebviewHtml} from './webviewHelper';
import {DEFAULT_PREFIXES} from '../webview/execution/rdfUtils';
import {getConfiguration} from '../config';
import {ExecutionFromWebviewMessage} from "../webview/protocol/execution";
import {roundToMagnitude, telemetry} from '../telemetry';

export interface ExecutionSessionState {
    executionId: string;
    mappingUri: string;
    fileName: string;
    status: 'idle' | 'running' | 'completed' | 'error';
    prefixes?: Record<string, string>;
    result?: ExecuteResult;
    error?: string;
    graphBackgroundMethod?: string;
}

export class ExecutionManager {
    private panels = new Map<string, vscode.WebviewPanel>();
    private sessions = new Map<string, ExecutionSessionState>();

    constructor(
        private readonly client: RmlDevClient,
        private readonly extensionUri: vscode.Uri
    ) {}

    public async executeMapping(documentUri?: vscode.Uri): Promise<void> {
        let targetUri = documentUri;
        if (!targetUri) {
            const editor = vscode.window.activeTextEditor;
            if (editor) {
                targetUri = editor.document.uri;
            }
        }

        if (!targetUri) {
            await vscode.window.showWarningMessage('No RML mapping document selected to run.');
            return;
        }

        if (!this.client.isConnected()) {
            await vscode.window.showErrorMessage('RML Dev Server is not connected. Please start or connect the server first.');
            return;
        }

        const uriStr = targetUri.toString();
        const fileName = path.basename(targetUri.fsPath || targetUri.path);

        let panel = this.panels.get(uriStr);
        if (!panel) {
            panel = vscode.window.createWebviewPanel(
                'rml-dev-server.executionResult',
                `Run: ${fileName}`,
                vscode.ViewColumn.Beside,
                {
                    enableScripts: true,
                    retainContextWhenHidden: true,
                    localResourceRoots: [
                        vscode.Uri.joinPath(this.extensionUri, 'out'),
                        this.extensionUri
                    ]
                }
            );

            this.panels.set(uriStr, panel);

            panel.onDidDispose(() => {
                this.panels.delete(uriStr);
                this.sessions.delete(uriStr);
            });

            panel.webview.onDidReceiveMessage(async (msg: ExecutionFromWebviewMessage) => {
                if (msg.command === 'ready') {
                    const session = this.sessions.get(uriStr);
                    if (session) {
                        panel?.webview.postMessage({
                            type: 'sessionState',
                            session
                        });
                    }
                } else if (msg.command === 'rerun') {
                    await this.executeMapping(targetUri);
                } else if (msg.command === 'clearCacheAndRerun') {
                    try {
                        await this.client.clearCache();
                        await vscode.window.showInformationMessage('RML execution cache cleared.');
                    } catch (err: any) {
                        await vscode.window.showWarningMessage(`Failed to clear cache: ${err.message}`);
                    }
                    await this.executeMapping(targetUri);
                } else if (msg.command === 'traceQuad') {
                    await vscode.commands.executeCommand(
                        'rml-dev-server.traceQuad',
                        uriStr,
                        {type: 'document', textDocument: {uri: uriStr}},
                        msg.quad
                    );
                } else if (msg.command === 'traceIteration') {
                    await vscode.commands.executeCommand(
                        'rml-dev-server.traceIteration',
                        uriStr,
                        {type: 'document', textDocument: {uri: uriStr}},
                        msg.iterationIndex
                    );
                } else if (msg.command === 'telemetry') {
                    telemetry.capture(msg.event, msg.properties);
                }
            });

            panel.webview.html = getWebviewHtml(panel.webview, this.extensionUri, 'execution');
        } else {
            panel.reveal(vscode.ViewColumn.Beside);
        }

        const config = getConfiguration();
        const executionId = crypto.randomUUID();
        const sessionState: ExecutionSessionState = {
            executionId,
            mappingUri: uriStr,
            fileName,
            status: 'running',
            prefixes: DEFAULT_PREFIXES,
            graphBackgroundMethod: config.graphBackgroundMethod,
        };
        this.sessions.set(uriStr, sessionState);

        await panel.webview.postMessage({
            type: 'startExecution',
            executionId,
            mappingUri: uriStr,
            fileName,
            prefixes: DEFAULT_PREFIXES,
            graphBackgroundMethod: config.graphBackgroundMethod
        });

        const chunkSub = this.client.onQuadChunk(executionId, (chunk) => {
            panel?.webview.postMessage({
                type: 'quadChunk',
                chunk
            });
        });

        const startTime = Date.now();

        await vscode.window.withProgress(
            {
                location: vscode.ProgressLocation.Notification,
                title: `Running RML mapping: ${fileName}`,
                cancellable: false
            },
            async (_progress) => {
                try {
                    const editor = vscode.window.activeTextEditor;
                    let structure: any;
                    if (editor && editor.document.uri.toString() === uriStr) {
                        const pos = editor.selection.active;
                        structure = {
                            type: 'cursor',
                            mappingFileUri: uriStr,
                            position: { line: pos.line, character: pos.character }
                        };
                    } else {
                        structure = {
                            type: 'id',
                            mappingFileUri: uriStr,
                            id: ''
                        };
                    }

                    const result = await this.client.execute({
                        structure,
                        executionId
                    });

                    const durationMs = Date.now() - startTime;

                    if (result.reportUpdate && result.reportUpdate.startsWith('Execution error:')) {
                        const errorMsg = result.reportUpdate;
                        sessionState.status = 'error';
                        sessionState.error = errorMsg;

                        telemetry.capture('mapping_execution_completed', {
                            duration_ms: durationMs,
                            triples_magnitude: 0,
                            success: false
                        });

                        await panel?.webview.postMessage({
                            type: 'executionError',
                            executionId,
                            error: errorMsg
                        });

                        await vscode.window.showErrorMessage(`Execution failed for ${fileName}: ${errorMsg}`);
                        return;
                    }

                    const finalPrefixes = (result as any).prefixMap && Object.keys((result as any).prefixMap).length > 0
                        ? (result as any).prefixMap
                        : DEFAULT_PREFIXES;
                    sessionState.status = 'completed';
                    sessionState.result = result;
                    sessionState.prefixes = finalPrefixes;

                    const totalTriples = Object.values(result.targets || {}).reduce(
                        (sum, t) => sum + (t.statementCount ?? t.statements?.length ?? 0),
                        0
                    );

                    telemetry.capture('mapping_execution_completed', {
                        duration_ms: durationMs,
                        triples_magnitude: roundToMagnitude(totalTriples),
                        success: true
                    });

                    await panel?.webview.postMessage({
                        type: 'executionComplete',
                        executionId,
                        result
                    });
                } catch (err: any) {
                    const durationMs = Date.now() - startTime;
                    const errorMsg = err.message || String(err);
                    sessionState.status = 'error';
                    sessionState.error = errorMsg;

                    telemetry.capture('mapping_execution_completed', {
                        duration_ms: durationMs,
                        triples_magnitude: 0,
                        success: false
                    });

                    await panel?.webview.postMessage({
                        type: 'executionError',
                        executionId,
                        error: errorMsg
                    });

                    await vscode.window.showErrorMessage(`Execution failed for ${fileName}: ${errorMsg}`);
                } finally {
                    chunkSub.dispose();
                }
            }
        );
    }

    public dispose(): void {
        for (const panel of this.panels.values()) {
            panel.dispose();
        }
        this.panels.clear();
        this.sessions.clear();
    }
}
