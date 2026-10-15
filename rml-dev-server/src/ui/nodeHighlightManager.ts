import * as vscode from 'vscode';
import {RmlDevClient} from '../client';
import {getConfiguration} from '../config';
import {isRmlDocument} from '../documentUtils';
import {PlanNodeItem, PlanResult} from '../generated/types';

export class NodeHighlightManager implements vscode.Disposable {
    private client: RmlDevClient;
    private selectedDecoration: vscode.TextEditorDecorationType;
    private allNodeDecorations: vscode.TextEditorDecorationType[] = [];
    private debounceTimer: NodeJS.Timeout | null = null;

    constructor(client: RmlDevClient) {
        this.client = client;
        this.selectedDecoration = vscode.window.createTextEditorDecorationType({
            backgroundColor: 'rgba(255, 215, 0, 0.15)',
            isWholeLine: false
        });
    }

    public update(editor: vscode.TextEditor | undefined): void {
        if (this.debounceTimer) {
            clearTimeout(this.debounceTimer);
        }
        this.debounceTimer = setTimeout(() => {
            this.doUpdate(editor);
        }, 50);
    }

    public clear(editor?: vscode.TextEditor): void {
        const targetEditor = editor || vscode.window.activeTextEditor;
        if (targetEditor) {
            targetEditor.setDecorations(this.selectedDecoration, []);
            for (const dec of this.allNodeDecorations) {
                targetEditor.setDecorations(dec, []);
            }
        }
        for (const dec of this.allNodeDecorations) {
            dec.dispose();
        }
        this.allNodeDecorations = [];
    }

    private async doUpdate(editor: vscode.TextEditor | undefined): Promise<void> {
        if (!editor || !this.client.isConnected()) {
            return;
        }

        const doc = editor.document;
        if (!isRmlDocument(doc)) {
            this.clear(editor);
            return;
        }

        const config = getConfiguration();
        const highlightSelected = config.nodeBackground;
        const highlightAll = config.nodeBackgroundAll;

        if (!highlightSelected && !highlightAll) {
            this.clear(editor);
            return;
        }

        try {
            const pos = editor.selection.active;
            const res: PlanResult = await this.client.plan({
                mappingFileUri: doc.uri.toString(),
                structure: {
                    type: 'cursor',
                    mappingFileUri: doc.uri.toString(),
                    position: {line: pos.line, character: pos.character}
                } as any
            });

            if (!res) {
                this.clear(editor);
                return;
            }

            // Clear previous "all" decorations
            for (const dec of this.allNodeDecorations) {
                editor.setDecorations(dec, []);
                dec.dispose();
            }
            this.allNodeDecorations = [];

            // Highlight selected node
            if (highlightSelected && res.selectedNodeId) {
                const selectedNode = this.findNodeById(res.roots || [], res.selectedNodeId);
                if (selectedNode && selectedNode.originRange && selectedNode.originRange.length > 0) {
                    const vsRanges = selectedNode.originRange.map(
                        (r) => new vscode.Range(r.start.line, r.start.character, r.end.line, r.end.character)
                    );
                    editor.setDecorations(this.selectedDecoration, vsRanges);
                } else {
                    editor.setDecorations(this.selectedDecoration, []);
                }
            } else {
                editor.setDecorations(this.selectedDecoration, []);
            }

            // Highlight all nodes if debug mode enabled
            if (highlightAll && res.roots) {
                const allNodes: PlanNodeItem[] = [];
                this.collectAllNodes(res.roots, allNodes);

                for (const node of allNodes) {
                    if (node.originRange && node.originRange.length > 0) {
                        const vsRanges = node.originRange.map(
                            (r) => new vscode.Range(r.start.line, r.start.character, r.end.line, r.end.character)
                        );
                        const color = this.getNodeColor(node.id);
                        const dec = vscode.window.createTextEditorDecorationType({
                            backgroundColor: color,
                            isWholeLine: false
                        });
                        this.allNodeDecorations.push(dec);
                        editor.setDecorations(dec, vsRanges);
                    }
                }
            }
        } catch {
            this.clear(editor);
        }
    }

    private findNodeById(nodes: PlanNodeItem[], id: string): PlanNodeItem | null {
        for (const node of nodes) {
            if (node.id === id) {
                return node;
            }
            if (node.children) {
                const found = this.findNodeById(node.children, id);
                if (found) return found;
            }
        }
        return null;
    }

    private collectAllNodes(nodes: PlanNodeItem[], acc: PlanNodeItem[]): void {
        for (const node of nodes) {
            acc.push(node);
            if (node.children) {
                this.collectAllNodes(node.children, acc);
            }
        }
    }

    private cyrb53(str: string, seed = 0) {
        let h1 = 0xdeadbeef ^ seed, h2 = 0x41c6ce57 ^ seed;
        for (let i = 0, ch; i < str.length; i++) {
            ch = str.charCodeAt(i);
            h1 = Math.imul(h1 ^ ch, 2654435761);
            h2 = Math.imul(h2 ^ ch, 1597334677);
        }
        h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507);
        h1 ^= Math.imul(h2 ^ (h2 >>> 13), 3266489909);
        h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507);
        h2 ^= Math.imul(h1 ^ (h1 >>> 13), 3266489909);
        return 4294967296 * (2097151 & h2) + (h1 >>> 0);
    };

    private getNodeColor(key: string): string {
        let hash = this.cyrb53(key)
        const hue = Math.abs(hash) % 360;
        return `hsla(${hue}, 70%, 55%, 0.18)`;
    }

    public dispose(): void {
        if (this.debounceTimer) {
            clearTimeout(this.debounceTimer);
        }
        this.selectedDecoration.dispose();
        for (const dec of this.allNodeDecorations) {
            dec.dispose();
        }
        this.allNodeDecorations = [];
    }
}
