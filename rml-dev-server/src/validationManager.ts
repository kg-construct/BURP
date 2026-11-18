import * as vscode from 'vscode';
import { RmlDevClient } from './client';
import { isRmlDocument, RML_DOCUMENT_SELECTOR } from './documentUtils';
import { telemetry } from './telemetry';

export class ValidationManager implements vscode.Disposable {
    private readonly disposables: vscode.Disposable[] = [];
    private lastHoveredKey: string | null = null;
    private lastHoverTime = 0;

    constructor(private readonly client: RmlDevClient) {
        // Hover telemetry: tracks when user inspects an error squiggle
        this.disposables.push(
            vscode.languages.registerHoverProvider(RML_DOCUMENT_SELECTOR, {
                provideHover: (document, position) => {
                    const diags = vscode.languages.getDiagnostics(document.uri);
                    if (!diags) return undefined;
                    for (const d of diags) {
                        if (d.range.contains(position)) {
                            const rerCode = typeof d.code === 'object' && d.code ? String(d.code.value) : String(d.code || '');
                            const key = `${document.uri.toString()}:${d.range.start.line}:${d.range.start.character}:${rerCode}`;
                            const now = Date.now();
                            if (this.lastHoveredKey === key && now - this.lastHoverTime < 2000) {
                                return undefined;
                            }
                            this.lastHoveredKey = key;
                            this.lastHoverTime = now;
                            telemetry.capture('validation_diagnostic_hover', {
                                severity: String(d.severity),
                                rerCodeUri: rerCode,
                                source: d.source || 'BURP Validator'
                            });
                            break;
                        }
                    }
                    return undefined;
                }
            })
        );

        // Manual validation trigger: save the active mapping document, prompting the language server to validate and push squiggles
        this.disposables.push(
            vscode.commands.registerCommand('rml.validateMapping', async () => {
                const editor = vscode.window.activeTextEditor;
                if (editor && isRmlDocument(editor.document)) {
                    await editor.document.save();
                    vscode.window.showInformationMessage('RML Mapping validation triggered.');
                }
            })
        );
    }

    public clear(uri: vscode.Uri): void {
        this.client.diagnostics?.delete(uri);
    }

    public dispose(): void {
        for (const d of this.disposables) {
            d.dispose();
        }
    }
}
