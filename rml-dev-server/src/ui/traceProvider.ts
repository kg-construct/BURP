import * as path from 'path';
import * as vscode from 'vscode';
import {RmlDevClient} from '../client';
import {StructureSelector, TraceIterationParams, TraceResult, TraceResultParams} from '../generated/types';
import {getWebviewHtml, getWebviewPanelOptions, revealRangeInEditor} from './webviewHelper';
import {TraceFromWebviewMessage, TraceQuadTarget, TraceToWebviewMessage} from '../webview/protocol/trace';
import {shortenIri} from '../webview/execution/rdfUtils';
import {roundToMagnitude, telemetry} from '../telemetry';

export { TraceQuadTarget };

interface TraceSessionState {
    panelKey: string;
    title: string;
    mode: 'iteration' | 'result';
    mappingUri: string;
    structure: StructureSelector;
    iterationIndex?: number;
    targetQuad?: TraceQuadTarget;
    lastResult?: TraceResult;
    error?: string;
}

export class TraceProvider {
    private panels = new Map<string, vscode.WebviewPanel>();
    private sessions = new Map<string, TraceSessionState>();

    constructor(
        private readonly client: RmlDevClient,
        private readonly extensionUri: vscode.Uri
    ) {}

    public async openTraceIteration(
        mappingUri: vscode.Uri | string,
        structure: StructureSelector,
        iterationIndex: number
    ): Promise<void> {
        const uriStr = typeof mappingUri === 'string' ? mappingUri : mappingUri.toString();
        const fileName = path.basename(typeof mappingUri === 'string' ? mappingUri : mappingUri.fsPath);
        const panelKey = `iter:${uriStr}:${iterationIndex}`;
        const title = `Trace: ${fileName} #${iterationIndex}`;

        const state: TraceSessionState = {
            panelKey,
            title,
            mode: 'iteration',
            mappingUri: uriStr,
            structure,
            iterationIndex
        };

        await this.showOrLoadPanel(state);
    }

    public async openTraceQuad(
        mappingUri: vscode.Uri | string,
        structure: StructureSelector,
        quad: TraceQuadTarget
    ): Promise<void> {
        const uriStr = typeof mappingUri === 'string' ? mappingUri : mappingUri.toString();
        const shortSubj = shortenIri(quad.subject).display;
        const shortPred = shortenIri(quad.predicate).display;
        const panelKey = `quad:${uriStr}:${quad.subject}:${quad.predicate}:${quad.object}`;
        const title = `Provenance: ${shortSubj} ${shortPred}`;

        const state: TraceSessionState = {
            panelKey,
            title,
            mode: 'result',
            mappingUri: uriStr,
            structure,
            targetQuad: quad
        };

        await this.showOrLoadPanel(state);
    }

    private async showOrLoadPanel(state: TraceSessionState): Promise<void> {
        let panel = this.panels.get(state.panelKey);

        if (panel) {
            panel.reveal(vscode.ViewColumn.Beside);
            await this.refreshTrace(state, panel);
            return;
        }

        panel = vscode.window.createWebviewPanel(
            'rml-dev-server.traceEditor',
            state.title,
            vscode.ViewColumn.Beside,
            getWebviewPanelOptions(this.extensionUri)
        );

        this.panels.set(state.panelKey, panel);
        this.sessions.set(state.panelKey, state);

        panel.webview.html = getWebviewHtml(panel.webview, this.extensionUri, 'trace');

        panel.onDidDispose(() => {
            this.panels.delete(state.panelKey);
            this.sessions.delete(state.panelKey);
        });

        panel.webview.onDidReceiveMessage(async (msg: TraceFromWebviewMessage) => {
            switch (msg.command) {
                case 'ready':
                case 'refresh':
                    await this.refreshTrace(state, panel!);
                    break;
                case 'selectRange':
                    if (msg.range && state.mappingUri) {
                        await revealRangeInEditor(state.mappingUri, msg.range);
                    }
                    break;
                case 'telemetry':
                    telemetry.capture(msg.event, msg.properties);
                    break;
            }
        });

        await this.refreshTrace(state, panel);
    }

    private postMessage(panel: vscode.WebviewPanel, msg: TraceToWebviewMessage): Thenable<boolean> {
        return panel.webview.postMessage(msg);
    }

    public async refreshTrace(state: TraceSessionState, panel: vscode.WebviewPanel): Promise<void> {
        if (!this.client.isConnected()) {
            await this.postMessage(panel, {
                command: 'traceError',
                error: 'RML Dev Server is not connected.'
            });
            return;
        }

        if (state.mode === 'iteration' && state.iterationIndex === undefined) {
            await this.postMessage(panel, {
                command: 'traceError',
                error: 'Iteration index is missing for iteration trace.'
            });
            return;
        }

        if (state.mode === 'result' && !state.targetQuad) {
            await this.postMessage(panel, {
                command: 'traceError',
                error: 'Target quad is missing for result provenance trace.'
            });
            return;
        }

        await this.postMessage(panel, { command: 'traceLoading' });
        const startTime = Date.now();

        try {
            let result: TraceResult;

            if (state.mode === 'iteration' && state.iterationIndex !== undefined) {
                const params: TraceIterationParams = {
                    structure: state.structure,
                    iterationIndex: state.iterationIndex
                };
                result = await this.client.traceIteration(params);
            } else if (state.mode === 'result' && state.targetQuad) {
                const params: TraceResultParams = {
                    structure: state.structure,
                    subject: state.targetQuad.subject,
                    predicate: state.targetQuad.predicate,
                    object: state.targetQuad.object,
                    graph: state.targetQuad.graph || '',
                    lineage: state.targetQuad.lineage
                };
                result = await this.client.traceResult(params);

                if (result.prefixMap) {
                    const shortSubj = shortenIri(state.targetQuad.subject, result.prefixMap).display;
                    const shortPred = shortenIri(state.targetQuad.predicate, result.prefixMap).display;
                    state.title = `Provenance: ${shortSubj} ${shortPred}`;
                    panel.title = state.title;
                }
            } else {
                return;
            }

            const durationMs = Date.now() - startTime;
            const nodeCount = result.nodes?.length ?? 0;
            telemetry.capture('trace_execution_completed', {
                duration_ms: durationMs,
                mode: state.mode,
                trace_node_magnitude: roundToMagnitude(nodeCount),
                success: true
            });

            state.lastResult = result;
            await this.postMessage(panel, {
                command: 'traceData',
                result,
                mode: state.mode,
                iterationIndex: state.iterationIndex,
                targetQuad: state.targetQuad,
                title: state.title
            });
        } catch (err: any) {
            const durationMs = Date.now() - startTime;
            telemetry.capture('trace_execution_completed', {
                duration_ms: durationMs,
                mode: state.mode,
                trace_node_magnitude: 0,
                success: false
            });

            const errorMsg = err.message || String(err);
            state.error = errorMsg;
            await this.postMessage(panel, {
                command: 'traceError',
                error: errorMsg
            });
        }
    }

    public dispose(): void {
        for (const panel of this.panels.values()) {
            panel.dispose();
        }
        this.panels.clear();
        this.sessions.clear();
    }
}
