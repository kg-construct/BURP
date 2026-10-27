import * as vscode from 'vscode';
import {RmlDevClient} from './client';
import {getConfiguration} from './config';
import {InlineEvaluateItem} from './generated/types';
import {telemetry} from './telemetry';
import {TypedDataContext} from './dataContext';

export class RmlEvalInlayHintsProvider implements vscode.InlayHintsProvider {
    private client: RmlDevClient;
    private onDidChangeInlayHintsEmitter = new vscode.EventEmitter<void>();
    public readonly onDidChangeInlayHints = this.onDidChangeInlayHintsEmitter.event;

    private cache = new Map<string, { version: number; hints: vscode.InlayHint[] }>();
    private dataContext: TypedDataContext = { type: 'range', startIndex: 0, endIndex: 0 };

    constructor(client: RmlDevClient) {
        this.client = client;
    }

    public setDataContext(context: TypedDataContext): void {
        this.dataContext = context;
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
            await this.client.didOpenTextDocument(document);

            const isDefaultContext = !this.dataContext || (this.dataContext.type === 'range' && this.dataContext.startIndex === 0 && this.dataContext.endIndex === 0);

            const hints: vscode.InlayHint[] = [];

            if (isDefaultContext || !getConfiguration().inlineHintsEnableEvaluation) {
                // Use standard LSP textDocument/inlayHint
                const lspHints = await this.client.inlayHint({
                    textDocument: { uri: document.uri.toString() },
                    range: {
                        start: { line: range.start.line, character: range.start.character },
                        end: { line: range.end.line, character: range.end.character }
                    }
                });

                if (token.isCancellationRequested || !lspHints) {
                    return undefined;
                }

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
            } else {
                // Use custom full document rml/inlineEvaluate for custom context override
                const items: InlineEvaluateItem[] = await this.client.inlineEvaluate({
                    mappingFileUri: document.uri.toString(),
                    dataContext: this.dataContext,
                    range: {
                        start: { line: range.start.line, character: range.start.character },
                        end: { line: range.end.line, character: range.end.character }
                    }
                });

                if (token.isCancellationRequested || !items) {
                    return undefined;
                }

                for (const item of items) {
                    if (!item.originRange || !item.formattedValues || item.formattedValues.length === 0) {
                        continue;
                    }

                    const display = item.formattedValues[0];
                    const rawDisplay = item.rawFormattedValues?.[0];
                    const hintPos = new vscode.Position(item.originRange.end.line, item.originRange.end.character);
                    const isParam = item.type === 'ParameterMap';
                    const hint = new vscode.InlayHint(
                        hintPos,
                        isParam ? ` : ${display}` : ` ⇾ ${display}`,
                        isParam || item.type === 'ReferenceSegment' ? vscode.InlayHintKind.Parameter : vscode.InlayHintKind.Type
                    );
                    hint.paddingLeft = isParam;

                    let tooltip = isParam
                        ? `**FnO Parameter**: \`${display}\`\n\nParameter ID: \`${item.id}\``
                        : `**${item.type} Evaluation** (\`${item.id}\`):\n\`\`\`turtle\n${display}\n\`\`\``;
                    if (!isParam && rawDisplay && rawDisplay !== display) {
                        tooltip += `\n*Full Term*: \`${rawDisplay}\``;
                    }
                    hint.tooltip = new vscode.MarkdownString(tooltip);
                    hints.push(hint);
                }
            }

            this.cache.set(document.uri.toString(), {
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

        const label = typeof hint.label === 'string'
            ? hint.label
            : (Array.isArray(hint.label) ? hint.label.map((p: any) => p.value).join('') : '');
        const kind: 'parameter' | 'type' | undefined =
            hint.kind === vscode.InlayHintKind.Parameter ? 'parameter' : (hint.kind === vscode.InlayHintKind.Type ? 'type' : undefined);

        telemetry.capture('inline_evaluate_hover', {
            hint_label: label,
            hint_kind: kind
        });

        return hint;
    }
}
