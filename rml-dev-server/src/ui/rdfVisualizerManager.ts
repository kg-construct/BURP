import * as path from 'path';
import * as crypto from 'crypto';
import * as vscode from 'vscode';
import { Parser, Quad, Term } from 'n3';
import { getWebviewHtml } from './webviewHelper';
import { DEFAULT_PREFIXES } from '../webview/execution/rdfUtils';
import { getConfiguration } from '../config';
import { ExecutionFromWebviewMessage, ExecutionToWebviewMessage } from "../webview/protocol/execution";
import { ExecuteResult, RdfStatement } from '../generated/types';
import { telemetry } from '../telemetry';

function n3TermToRdfTerm(term: Term): any {
    if (term.termType === 'NamedNode') {
        return { uri: term.value, targets: [] };
    }
    if (term.termType === 'BlankNode') {
        return { id: term.value, targets: [] };
    }
    if (term.termType === 'Literal') {
        const result: any = { value: term.value, targets: [] };
        if (term.language) {
            result.language = term.language;
        } else if (term.datatype && term.datatype.value) {
            result.datatype = { uri: term.datatype.value, targets: [] };
        }
        return result;
    }
    return { uri: term.value, targets: [] };
}

function n3QuadToRdfStatement(q: Quad): RdfStatement {
    return {
        subject: n3TermToRdfTerm(q.subject),
        predicate: n3TermToRdfTerm(q.predicate),
        object: n3TermToRdfTerm(q.object),
        graph: q.graph.termType !== 'DefaultGraph' && q.graph.value ? { uri: q.graph.value, targets: [] } : undefined,
        targets: []
    };
}

interface RdfVisualizerSessionState {
    executionId: string;
    mappingUri: string;
    fileName: string;
    status: 'idle' | 'running' | 'completed' | 'error';
    prefixes?: Record<string, string>;
    result?: any;
    error?: string;
    graphBackgroundMethod?: string;
}

export class RdfVisualizerManager {
    private panels = new Map<string, vscode.WebviewPanel>();
    private sessions = new Map<string, RdfVisualizerSessionState>();

    constructor(private readonly extensionUri: vscode.Uri) {}

    public async visualizeRdf(documentUri?: vscode.Uri): Promise<void> {
        let targetUri = documentUri;
        if (!targetUri) {
            const editor = vscode.window.activeTextEditor;
            if (editor) {
                targetUri = editor.document.uri;
            }
        }

        if (!targetUri) {
            vscode.window.showWarningMessage('No RDF document selected to visualize.');
            return;
        }

        const uriStr = targetUri.toString();
        const fileName = path.basename(targetUri.fsPath || targetUri.path);

        let panel = this.panels.get(uriStr);
        if (!panel) {
            panel = vscode.window.createWebviewPanel(
                'rml-dev-server.rdfVisualizer',
                `RDF: ${fileName}`,
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
                    await this.loadAndParseRdf(targetUri!, panel!);
                } else if (msg.command === 'telemetry') {
                    telemetry.capture(msg.event, msg.properties);
                }
            });

            panel.webview.html = getWebviewHtml(panel.webview, this.extensionUri, 'rdfviewer');
        } else {
            panel.reveal(vscode.ViewColumn.Beside);
        }

        await this.loadAndParseRdf(targetUri, panel);
    }

    private async loadAndParseRdf(targetUri: vscode.Uri, panel: vscode.WebviewPanel): Promise<void> {
        const uriStr = targetUri.toString();
        const fileName = path.basename(targetUri.fsPath || targetUri.path);
        const executionId = crypto.randomUUID();
        const config = getConfiguration();

        const sessionState: RdfVisualizerSessionState = {
            executionId,
            mappingUri: uriStr,
            fileName,
            status: 'running',
            prefixes: { ...DEFAULT_PREFIXES },
            graphBackgroundMethod: config.graphBackgroundMethod
        };
        this.sessions.set(uriStr, sessionState);

        panel.webview.postMessage({
            type: 'startExecution',
            executionId,
            mappingUri: uriStr,
            fileName,
            prefixes: DEFAULT_PREFIXES,
            graphBackgroundMethod: config.graphBackgroundMethod
        });

        try {
            // Read document content
            let docText = '';
            const doc = vscode.workspace.textDocuments.find((d) => d.uri.toString() === uriStr);
            if (doc) {
                docText = doc.getText();
            } else {
                const fileBytes = await vscode.workspace.fs.readFile(targetUri);
                docText = Buffer.from(fileBytes).toString('utf-8');
            }

            // Determine RDF format based on extension
            const ext = path.extname(targetUri.fsPath || targetUri.path).toLowerCase();
            let format: string | undefined;
            if (ext === '.nt') {
                format = 'N-Triples';
            } else if (ext === '.nq') {
                format = 'N-Quads';
            } else if (ext === '.trig') {
                format = 'TriG';
            } else if (ext === '.n3') {
                format = 'Notation3';
            } else {
                format = 'Turtle';
            }

            const prefixMap: Record<string, string> = { ...DEFAULT_PREFIXES };
            const parser = new Parser({ format });
            const quads: Quad[] = parser.parse(docText, null, (prefix, prefixIri) => {
                if (prefix && prefix !== '_') {
                    prefixMap[prefix] = typeof prefixIri === 'string' ? prefixIri : (prefixIri as any).value || String(prefixIri);
                }
            });

            const statements: RdfStatement[] = quads.map(n3QuadToRdfStatement);

            const result: ExecuteResult = {
                reportUpdate: '',
                prefixMap,
                logicalSources: {},
                targets: {
                    default: {
                        targetId: 'default',
                        statements,
                        statementCount: statements.length
                    }
                }
            };

            sessionState.status = 'completed';
            sessionState.result = result;
            sessionState.prefixes = prefixMap;

            panel.webview.postMessage({
                type: 'executionComplete',
                executionId,
                result
            });
        } catch (err: any) {
            const errorMsg = err.message || String(err);
            sessionState.status = 'error';
            sessionState.error = errorMsg;

            panel.webview.postMessage({
                type: 'executionError',
                executionId,
                error: errorMsg
            });

            vscode.window.showErrorMessage(`Failed to parse RDF file ${fileName}: ${errorMsg}`);
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
