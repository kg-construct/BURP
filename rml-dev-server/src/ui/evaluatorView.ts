import * as vscode from 'vscode';
import {RmlDevClient} from '../client';
import {EvaluateParams, EvaluateResult, ReferenceDependency} from '../generated/types';
import {getWebviewHtml, getWebviewOptions} from './webviewHelper';
import {DEFAULT_PREFIXES} from '../webview/execution/rdfUtils';
import {EvaluatorFromWebviewMessage, EvaluatorToWebviewMessage} from "../webview/protocol/evaluator";
import {telemetry} from '../telemetry';
import {TypedDataContext} from '../dataContext';

export type EvaluatorMessage = EvaluatorToWebviewMessage;

export class EvaluatorViewProvider implements vscode.WebviewViewProvider {
    public static readonly viewType = 'rml-dev-server.evaluatorView';
    private view?: vscode.WebviewView;
    private client: RmlDevClient;
    private extensionUri: vscode.Uri;
    private activeFileUri: string | null = null;
    private activePosition: vscode.Position = new vscode.Position(0, 0);
    private dataContext: TypedDataContext = {type: 'range', startIndex: 0, endIndex: 0};

    private onDataContextChanged?: (ctx: TypedDataContext) => void;
    private getSelectedNodeType?: () => string | undefined;

    constructor(
        client: RmlDevClient,
        extensionUri: vscode.Uri,
        onDataContextChanged?: (ctx: TypedDataContext) => void,
        getSelectedNodeType?: () => string | undefined
    ) {
        this.client = client;
        this.extensionUri = extensionUri;
        this.onDataContextChanged = onDataContextChanged;
        this.getSelectedNodeType = getSelectedNodeType;
    }

    public resolveWebviewView(
        webviewView: vscode.WebviewView,
        context: vscode.WebviewViewResolveContext,
        token: vscode.CancellationToken
    ) {
        this.view = webviewView;
        webviewView.webview.options = getWebviewOptions(this.extensionUri);

        webviewView.webview.html = getWebviewHtml(webviewView.webview, this.extensionUri, 'evaluator');

        webviewView.webview.onDidReceiveMessage(async (msg: EvaluatorFromWebviewMessage) => {
            switch (msg.command) {
                case 'runEvaluation':
                    this.dataContext = msg.dataContext;
                    if (this.onDataContextChanged) {
                        this.onDataContextChanged(this.dataContext);
                    }
                    await this.evaluateCurrent();
                    break;
                case 'fetchDependencies':
                    await this.fetchAndSendDependencies();
                    break;
                case 'refresh':
                    await this.evaluateCurrent();
                    break;
                case 'telemetry':
                    telemetry.capture(msg.event, msg.properties);
                    break;
            }
        });

        this.evaluateCurrent();
    }

    private postMessage(msg: EvaluatorToWebviewMessage): Thenable<boolean> {
        return this.view ? this.view.webview.postMessage(msg) : Promise.resolve(false);
    }

    public async updateCursor(document: vscode.TextDocument, position: vscode.Position) {
        this.activeFileUri = document.uri.toString();
        this.activePosition = position;
        await this.evaluateCurrent();
    }

    public async fetchAndSendDependencies() {
        if (!this.view || !this.client.isConnected() || !this.activeFileUri) {
            return;
        }
        try {
            const deps: ReferenceDependency[] = await this.client.referencesDependencies({
                structure: {
                    type: 'cursor',
                    mappingFileUri: this.activeFileUri,
                    position: {line: this.activePosition.line, character: this.activePosition.character}
                } as any
            });
            this.postMessage({
                command: 'renderDependencies',
                dependencies: deps
            });
        } catch (err: any) {
            console.error('[EvaluatorViewProvider] Error fetching reference dependencies:', err);
        }
    }

    public async evaluateCurrent() {
        if (!this.view || !this.client.isConnected() || !this.activeFileUri) {
            this.postMessage({
                command: 'render',
                results: undefined,
                emptyMessage: 'Open a mapping file and connect server to inspect evaluated values.'
            });
            return;
        }

        try {
            const params: EvaluateParams = {
                structure: {
                    type: 'cursor',
                    mappingFileUri: this.activeFileUri,
                    position: {line: this.activePosition.line, character: this.activePosition.character}
                } as any,
                dataContext: this.dataContext
            };

            const results: EvaluateResult[] = await this.client.evaluate(params);
            const nodeType = this.getSelectedNodeType ? this.getSelectedNodeType() : undefined;
            const hasResults = results && results.some(r => r.generatedRdf && r.generatedRdf.length > 0);
            const hasErrors = results && results.some(r => (r.diagnostics && r.diagnostics.length > 0) || !!r.rerDiagnostic);

            telemetry.capture('evaluate', {
                node_type: nodeType,
                data_context_type: this.dataContext.type,
                has_results: hasResults,
                has_errors: hasErrors
            });

            const prefixes = (results && results[0]?.prefixMap && Object.keys(results[0].prefixMap).length > 0)
                ? results[0].prefixMap
                : DEFAULT_PREFIXES;

            await this.postMessage({
                command: 'render',
                results,
                prefixes,
                cursor: {line: this.activePosition.line + 1, character: this.activePosition.character + 1},
                fileUri: this.activeFileUri
            });
        } catch (err: any) {
            const nodeType = this.getSelectedNodeType ? this.getSelectedNodeType() : undefined;
            telemetry.capture('evaluate', {
                node_type: nodeType,
                data_context_type: this.dataContext.type,
                has_results: false,
                has_errors: true
            });

            await this.postMessage({
                command: 'render',
                results: undefined,
                emptyMessage: `Evaluation error: ${err.message}`
            });
        }
    }
}
