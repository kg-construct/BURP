import * as vscode from 'vscode';
import {RmlDevClient} from './client';
import {isRmlDocument, RML_DOCUMENT_SELECTOR} from './documentUtils';
import {RmlDiagnostic} from './generated/types';
import {telemetry} from './telemetry';

export class ValidationManager implements vscode.Disposable {
    private readonly diagnosticCollection: vscode.DiagnosticCollection;
    private readonly documentDiagnostics = new Map<string, RmlDiagnostic[]>();
    private validationTimeout: NodeJS.Timeout | null = null;
    private readonly disposables: vscode.Disposable[] = [];
    private lastHoveredKey: string | null = null;
    private lastHoverTime: number = 0;

    constructor(private readonly client: RmlDevClient) {
        this.diagnosticCollection = vscode.languages.createDiagnosticCollection('rml');
        this.disposables.push(this.diagnosticCollection);
        this.disposables.push(
            vscode.commands.registerCommand('rml-dev-server.validateMapping', async () => {
                const editor = vscode.window.activeTextEditor;
                if (editor && isRmlDocument(editor.document)) {
                    await this.runValidation(editor.document);
                    vscode.window.showInformationMessage('RML Mapping validated.');
                }
            })
        );
        this.disposables.push(
            vscode.languages.registerHoverProvider(RML_DOCUMENT_SELECTOR, {
                provideHover: (document, position) => {
                    return this.handleHover(document, position);
                }
            })
        );
    }

    public async runValidation(document: vscode.TextDocument): Promise<void> {
        if (!this.client.isConnected() || !isRmlDocument(document)) {
            return;
        }
        try {
            const uri = document.uri.toString();
            const diagnostics = await this.client.validate({mappingFileUri: uri});
            this.documentDiagnostics.set(uri, diagnostics || []);
            const vsDiagnostics: vscode.Diagnostic[] = (diagnostics || []).map((d) => {
                const range = d.range
                    ? new vscode.Range(
                        d.range.start.line,
                        d.range.start.character,
                        d.range.end.line,
                        d.range.end.character
                    )
                    : new vscode.Range(0, 0, 0, 0);
                let severity = vscode.DiagnosticSeverity.Error;
                if (d.severity === 'Warning' || (typeof d.severity === 'number' && d.severity === 2)) {
                    severity = vscode.DiagnosticSeverity.Warning;
                } else if (d.severity === 'Information' || (typeof d.severity === 'number' && d.severity === 3)) {
                    severity = vscode.DiagnosticSeverity.Information;
                } else if (d.severity === 'Hint' || (typeof d.severity === 'number' && d.severity === 4)) {
                    severity = vscode.DiagnosticSeverity.Hint;
                }
                const diag = new vscode.Diagnostic(range, d.message, severity);
                diag.source = d.source || 'BURP Validator';
                diag.code = d.rerCodeUri || undefined;
                return diag;
            });
            this.diagnosticCollection.set(document.uri, vsDiagnostics);
        } catch {
            // ignore validation failures
        }
    }

    private handleHover(document: vscode.TextDocument, position: vscode.Position): vscode.ProviderResult<vscode.Hover> {
        const uri = document.uri.toString();
        const rmlDiags = this.documentDiagnostics.get(uri);
        if (!rmlDiags || rmlDiags.length === 0) {
            return undefined;
        }

        for (const d of rmlDiags) {
            if (!d.range) {
                continue;
            }
            const range = new vscode.Range(
                d.range.start.line,
                d.range.start.character,
                d.range.end.line,
                d.range.end.character
            );
            if (range.contains(position)) {
                const key = `${uri}:${d.range.start.line}:${d.range.start.character}:${d.rerCodeUri || ''}`;
                const now = Date.now();
                if (this.lastHoveredKey === key && now - this.lastHoverTime < 2000) {
                    return undefined;
                }
                this.lastHoveredKey = key;
                this.lastHoverTime = now;

                telemetry.capture('validation_diagnostic_hover', {
                    severity: String(d.severity ?? 'Error'),
                    rerCodeUri: d.rerCodeUri ?? '',
                    source: d.source || 'BURP Validator'
                });
                break;
            }
        }
        return undefined;
    }

    public scheduleValidation(document: vscode.TextDocument, delayMs = 300): void {
        if (this.validationTimeout) {
            clearTimeout(this.validationTimeout);
        }
        this.validationTimeout = setTimeout(() => {
            this.runValidation(document);
        }, delayMs);
    }

    public clear(uri: vscode.Uri): void {
        this.documentDiagnostics.delete(uri.toString());
        this.diagnosticCollection.delete(uri);
    }

    public dispose(): void {
        if (this.validationTimeout) {
            clearTimeout(this.validationTimeout);
            this.validationTimeout = null;
        }
        for (const d of this.disposables) {
            d.dispose();
        }
    }
}
