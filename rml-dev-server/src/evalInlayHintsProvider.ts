import * as vscode from 'vscode';
import {RmlDevClient} from './client';
import {telemetry} from './telemetry';
import {TypedDataContext} from './dataContext';

export class RmlEvalInlayHintsProvider implements vscode.InlayHintsProvider {
    private client: RmlDevClient;
    private onDidChangeInlayHintsEmitter = new vscode.EventEmitter<void>();
    public readonly onDidChangeInlayHints = this.onDidChangeInlayHintsEmitter.event;

    private cache = new Map<string, { version: number; hints: vscode.InlayHint[] }>();
    private syncedContexts = new Map<string, TypedDataContext>();
    private dataContext: TypedDataContext = { type: 'range', startIndex: 0, endIndex: 0 };

    constructor(client: RmlDevClient) {
        this.client = client;
    }

    public setDataContext(context: TypedDataContext, uri?: string): void {
        this.dataContext = context;
        const targetUri = uri ?? vscode.window.activeTextEditor?.document.uri.toString();
        if (targetUri && this.client.isConnected()) {
            this.client.setDataContext({
                mappingFileUri: targetUri,
                dataContext: context
            }).catch(err => {
                console.error('[RmlEvalInlayHintsProvider] Failed to set data context:', err);
            });
            this.syncedContexts.set(targetUri, context);
        }
        this.refresh();
    }

    public getDataContext(): TypedDataContext {
        return this.dataContext;
    }

    public refresh(): void {
        this.cache.clear();
        this.onDidChangeInlayHintsEmitter.fire();
    }

    public async provideInlayHints(
        document: vscode.TextDocument,
        range: vscode.Range,
        token: vscode.CancellationToken
    ): Promise<vscode.InlayHint[] | undefined> {
        if (!this.client.isConnected()) {
            return undefined;
        }

        const cached = this.cache.get(document.uri.toString());
        if (cached && cached.version === document.version) {
            return cached.hints;
        }

        try {
            const docUri = document.uri.toString();
            if (this.syncedContexts.get(docUri) !== this.dataContext) {
                await this.client.setDataContext({
                    mappingFileUri: docUri,
                    dataContext: this.dataContext
                });
                this.syncedContexts.set(docUri, this.dataContext);
            }

            // Standard LSP textDocument/inlayHint
            const lspHints = await this.client.inlayHint({
                textDocument: {uri: docUri},
                range: {
                    start: {line: range.start.line, character: range.start.character},
                    end: {line: range.end.line, character: range.end.character}
                }
            });

            if (token.isCancellationRequested || !lspHints) {
                return undefined;
            }

            const hints: vscode.InlayHint[] = [];
            for (const h of lspHints) {
                const label = typeof h.label === 'string' ? h.label : (Array.isArray(h.label) ? h.label.map((p: any) => p.value).join('') : '');
                const hintPos = new vscode.Position(h.position.line, h.position.character);
                const hint = new vscode.InlayHint(
                    hintPos,
                    label,
                    h.kind === 2 ? vscode.InlayHintKind.Parameter : vscode.InlayHintKind.Type
                );
                hint.paddingLeft = h.paddingLeft;
                if (h.tooltip) {
                    hint.tooltip = typeof h.tooltip === 'string'
                        ? new vscode.MarkdownString(h.tooltip)
                        : new vscode.MarkdownString(h.tooltip.value);
                }
                hints.push(hint);
            }

            this.cache.set(docUri, {
                version: document.version,
                hints
            });

            return hints;
        } catch (err) {
            console.error('[RmlEvalInlayHintsProvider] Error calculating inlay hints:', err);
            return undefined;
        }
    }

    public async resolveInlayHint(
        hint: vscode.InlayHint,
        token: vscode.CancellationToken
    ): Promise<vscode.InlayHint> {
        if (token.isCancellationRequested) {
            return hint;
        }

        const kind: 'parameter' | 'type' | undefined =
            hint.kind === vscode.InlayHintKind.Parameter ? 'parameter' : (hint.kind === vscode.InlayHintKind.Type ? 'type' : undefined);

        telemetry.capture('inline_evaluate_hover', {
            hint_kind: kind
        });

        return hint;
    }
}
