import * as vscode from 'vscode';
import {RmlDevClient} from './client';

export class RmlCompletionItemProvider implements vscode.CompletionItemProvider {
    private client: RmlDevClient;

    constructor(client: RmlDevClient) {
        this.client = client;
    }

    public async provideCompletionItems(
        document: vscode.TextDocument,
        position: vscode.Position,
        token: vscode.CancellationToken
    ): Promise<vscode.CompletionItem[] | vscode.CompletionList | null | undefined> {
        if (!this.client.isConnected()) {
            return undefined;
        }

        try {
            await this.client.didOpenTextDocument(document);

            const result: any = await this.client.completion({
                textDocument: { uri: document.uri.toString() },
                position: { line: position.line, character: position.character }
            });

            if (!result) {
                return undefined;
            }

            const rawItems: any[] = Array.isArray(result) ? result : (result.items || []);
            if (rawItems.length === 0) {
                return undefined;
            }

            const items = rawItems.map((comp: any, idx: number) => {
                let kind = vscode.CompletionItemKind.Field;
                if (comp.kind === 10 || comp.kind === 'Property') {
                    kind = vscode.CompletionItemKind.Property;
                } else if (comp.kind === 3 || comp.kind === 'Function') {
                    kind = vscode.CompletionItemKind.Function;
                } else if (comp.kind === 5 || comp.kind === 'Field') {
                    kind = vscode.CompletionItemKind.Field;
                }

                const item = new vscode.CompletionItem(comp.label, kind);
                item.sortText = comp.sortText || String(idx).padStart(4, '0');
                item.detail = comp.detail;

                if (comp.documentation) {
                    item.documentation = typeof comp.documentation === 'string'
                        ? new vscode.MarkdownString(comp.documentation)
                        : new vscode.MarkdownString(comp.documentation.value || '');
                }

                if (comp.textEdit) {
                    const r = comp.textEdit.range;
                    item.range = new vscode.Range(
                        new vscode.Position(r.start.line, r.start.character),
                        new vscode.Position(r.end.line, r.end.character)
                    );
                    item.insertText = comp.textEdit.newText;
                } else if (comp.insertText) {
                    item.insertText = comp.insertText;
                }

                return item;
            });

            return Array.isArray(result) ? items : new vscode.CompletionList(items, result.isIncomplete || false);
        } catch (err) {
            console.error('Failed to get completions from RML Dev Server:', err);
            return undefined;
        }
    }
}
