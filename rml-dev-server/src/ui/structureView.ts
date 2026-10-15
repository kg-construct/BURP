import * as vscode from 'vscode';
import {RmlDevClient} from '../client';
import {PlanNodeItem, PlanResult} from '../generated/types';
import {isRmlDocument} from '../documentUtils';
import {getWebviewHtml, revealRangeInEditor} from './webviewHelper';
import {PlanFromWebviewMessage, PlanToWebviewMessage} from "../webview/protocol/plan";
import {telemetry} from '../telemetry';

export class StructureViewProvider implements vscode.WebviewViewProvider {
    public static readonly viewType = 'rml-dev-server.structureView';
    private view?: vscode.WebviewView;
    private client: RmlDevClient;
    private extensionUri: vscode.Uri;
    private currentPlan: PlanResult | null = null;
    private activeFileUri: string | null = null;
    private activePosition: vscode.Position = new vscode.Position(0, 0);

    constructor(client: RmlDevClient, extensionUri: vscode.Uri) {
        this.client = client;
        this.extensionUri = extensionUri;
    }

    private syncActiveEditor(): boolean {
        const editor = vscode.window.activeTextEditor;
        if (editor && isRmlDocument(editor.document)) {
            this.activeFileUri = editor.document.uri.toString();
            this.activePosition = editor.selection.active;
            return true;
        }
        return false;
    }

    public resolveWebviewView(
        webviewView: vscode.WebviewView,
        context: vscode.WebviewViewResolveContext,
        token: vscode.CancellationToken
    ) {
        this.view = webviewView;
        webviewView.webview.options = {
            enableScripts: true,
            localResourceRoots: [vscode.Uri.joinPath(this.extensionUri, 'out')]
        };

        webviewView.webview.html = getWebviewHtml(webviewView.webview, this.extensionUri, 'structure');

        webviewView.onDidChangeVisibility(async () => {
            if (webviewView.visible) {
                this.syncActiveEditor();
                await this.refresh();
            }
        });

        webviewView.webview.onDidReceiveMessage(async (msg: PlanFromWebviewMessage) => {
            switch (msg.command) {
                case 'selectRange':
                    if (msg.fileUri && (msg.range || (msg.ranges && msg.ranges.length > 0))) {
                        const primaryRange = msg.range || msg.ranges![0];
                        await revealRangeInEditor(msg.fileUri, primaryRange, msg.ranges);
                    }
                    break;
                case 'openIssues':
                    vscode.env.openExternal(vscode.Uri.parse('https://github.com/kg-construct/BURP/issues'));
                    break;
                case 'nodeExpanded':
                    if (msg.nodeId && msg.nodeType) {
                        telemetry.capture('plan_node_expanded', {
                            node_id: msg.nodeId,
                            node_type: msg.nodeType
                        });
                    }
                    break;
                case 'ready':
                case 'refresh':
                    this.syncActiveEditor();
                    await this.refresh();
                    break;
                case 'telemetry':
                    telemetry.capture(msg.event, msg.properties);
                    break;
            }
        });

        this.syncActiveEditor();
        this.refresh();
    }

    public async updateCursor(document: vscode.TextDocument, position: vscode.Position) {
        this.activeFileUri = document.uri.toString();
        this.activePosition = position;
        await this.refresh();
    }

    private postMessage(msg: PlanToWebviewMessage): Thenable<boolean> {
        return this.view ? this.view.webview.postMessage(msg) : Promise.resolve(false);
    }

    public async refresh() {
        if (!this.activeFileUri) {
            this.syncActiveEditor();
        }
        if (!this.view || !this.client.isConnected() || !this.activeFileUri) {
            this.postMessage({
                command: 'render',
                roots: [],
                selectedNodeId: null,
                emptyMessage: 'Open a mapping file and connect server to view hierarchy.'
            });
            return;
        }

        try {
            const res: PlanResult = await this.client.plan({
                mappingFileUri: this.activeFileUri,
                structure: {
                    type: 'cursor',
                    mappingFileUri: this.activeFileUri,
                    position: { line: this.activePosition.line, character: this.activePosition.character }
                } as any
            });
            this.currentPlan = res ?? null;

            this.postMessage({
                command: 'render',
                roots: res?.roots ?? [],
                selectedNodeId: res?.selectedNodeId ?? null,
                fileUri: this.activeFileUri
            });
        } catch (err: any) {
            this.postMessage({
                command: 'render',
                roots: [],
                selectedNodeId: null,
                emptyMessage: `Error loading plan: ${err.message}`
            });
        }
    }

    public getSelectedNodeType(): string | undefined {
        if (!this.currentPlan || !this.currentPlan.selectedNodeId) {
            return undefined;
        }
        return this.findNodeType(this.currentPlan.roots, this.currentPlan.selectedNodeId);
    }

    private findNodeType(nodes: PlanNodeItem[] | undefined, id: string): string | undefined {
        if (!nodes) {
            return undefined;
        }
        for (const node of nodes) {
            if (node.id === id) {
                return node.type;
            }
            if (node.children && node.children.length > 0) {
                const found = this.findNodeType(node.children, id);
                if (found) {
                    return found;
                }
            }
        }
        return undefined;
    }
}
