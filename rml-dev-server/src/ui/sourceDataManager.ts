import * as vscode from 'vscode';
import {RmlDevClient} from '../client';
import {SourceDataParams, SourceDataResult, StructureSelector} from '../generated/types';
import {getWebviewHtml} from './webviewHelper';
import {SourceFromWebviewMessage, SourceToWebviewMessage} from "../webview/protocol/source";
import {telemetry} from '../telemetry';

export class SourceDataManager {
    private currentPanel: vscode.WebviewPanel | null = null;
    private currentSelector: StructureSelector | null = null;
    private currentMappingUri: string = '';

    constructor(
        private readonly client: RmlDevClient,
        private readonly extensionUri: vscode.Uri
    ) {}

    public async openPreview(selector?: any, mappingUri?: string): Promise<void> {
        let normalizedSelector: StructureSelector | null = null;
        if (selector) {
            if (selector.type) {
                normalizedSelector = selector as StructureSelector;
            } else if (selector.iri) {
                normalizedSelector = {
                    type: 'iri',
                    iri: selector.iri,
                    mappingFileUri: selector.mappingFileUri || mappingUri || ''
                };
            } else if (selector.id) {
                normalizedSelector = {
                    type: 'id',
                    id: selector.id,
                    mappingFileUri: selector.mappingFileUri || mappingUri || ''
                };
            } else if (selector.position) {
                normalizedSelector = {
                    type: 'cursor',
                    position: selector.position,
                    mappingFileUri: selector.mappingFileUri || mappingUri || ''
                };
            }
        }

        if (!normalizedSelector) {
            const editor = vscode.window.activeTextEditor;
            if (editor) {
                const pos = editor.selection.active;
                normalizedSelector = {
                    type: 'cursor',
                    mappingFileUri: editor.document.uri.toString(),
                    position: { line: pos.line, character: pos.character }
                };
            }
        }

        if (!normalizedSelector) {
            return;
        }

        if (!(normalizedSelector as any).mappingFileUri) {
            const activeUri = vscode.window.activeTextEditor?.document.uri.toString();
            if (activeUri) {
                normalizedSelector = { ...(normalizedSelector as any), mappingFileUri: activeUri };
            }
        }

        this.currentSelector = normalizedSelector;
        this.currentMappingUri = (normalizedSelector as any).mappingFileUri || mappingUri || '';

        const title = (normalizedSelector as any).iri || (normalizedSelector as any).id || 'Source';

        if (this.currentPanel) {
            this.currentPanel.title = `Table: ${title}`;
            this.currentPanel.reveal(vscode.ViewColumn.Beside);
            await this.loadData(0, 50, true);
        } else {
            this.currentPanel = vscode.window.createWebviewPanel(
                'rml-dev-server.sourceDataViewer',
                `Table: ${title}`,
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

            this.currentPanel.onDidDispose(() => {
                this.currentPanel = null;
                this.currentSelector = null;
            });

            this.currentPanel.webview.onDidReceiveMessage(async (msg: SourceFromWebviewMessage) => {
                if (msg.command === 'ready') {
                    await this.loadData(0, 50, true);
                } else if (msg.command === 'fetchMore') {
                    await this.loadData(msg.offset || 0, msg.limit || 50, false);
                } else if (msg.command === 'openInEditor') {
                    try {
                        const doc = await vscode.workspace.openTextDocument({
                            content: msg.content,
                            language: msg.language || 'text'
                        });
                        await vscode.window.showTextDocument(doc, { preview: true, viewColumn: vscode.ViewColumn.Active });
                    } catch (e: any) {
                        vscode.window.showErrorMessage(`Failed to open in editor: ${e.message || String(e)}`);
                    }
                } else if (msg.command === 'traceIteration') {
                    vscode.commands.executeCommand(
                        'rml-dev-server.traceIteration',
                        this.currentMappingUri,
                        this.currentSelector,
                        msg.iterationIndex
                    );
                } else if (msg.command === 'telemetry') {
                    telemetry.capture(msg.event, msg.properties);
                }
            });

            this.currentPanel.webview.html = getWebviewHtml(
                this.currentPanel.webview,
                this.extensionUri,
                'sourceDataTable'
            );
        }
    }

    public async openSourceData(selector?: any, mappingUri?: string): Promise<void> {
        return this.openPreview(selector, mappingUri);
    }

    private postMessage(msg: SourceToWebviewMessage): Thenable<boolean> {
        return this.currentPanel ? this.currentPanel.webview.postMessage(msg) : Promise.resolve(false);
    }

    private async loadData(offset: number, limit: number, isInitial: boolean): Promise<void> {
        if (!this.currentPanel || !this.currentSelector) {
            return;
        }

        if (!this.client.isConnected()) {
            this.postMessage({
                command: 'setError',
                error: 'RML Dev Server is not connected. Please start/connect the server.'
            });
            return;
        }

        try {
            this.postMessage({
                command: 'setLoading',
                isInitial
            });

            const params: SourceDataParams = {
                structure: this.currentSelector,
                offset,
                limit,
                dataContext: { type: 'all' }
            };

            const result: SourceDataResult = await this.client.getSourceData(params);

            if (isInitial) {
                const format: 'table' | 'json' | 'xml' =
                    (result.format === 'json' || result.format === 'xml') ? result.format : 'table';
                telemetry.capture('abstract_logical_source_view', {
                    source_type: result.sourceType || 'LogicalSource',
                    format
                });
            }

            const titlePart = result.structureId || result.structureResource || 'Source';
            if (result.format === 'json') {
                this.currentPanel.title = `JSON: ${titlePart}`;
            } else if (result.format === 'xml') {
                this.currentPanel.title = `XML: ${titlePart}`;
            } else if (result.sourceType) {
                this.currentPanel.title = `Table (${result.sourceType}): ${titlePart}`;
            }

            this.postMessage({
                command: 'renderPreview',
                data: result
            });
        } catch (err: any) {
            this.postMessage({
                command: 'setError',
                error: err.message || String(err)
            });
        }
    }
}
