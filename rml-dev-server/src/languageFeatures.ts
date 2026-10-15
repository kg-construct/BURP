import * as vscode from 'vscode';
import {RmlDevClient} from './client';
import {RmlCompletionItemProvider} from './completionProvider';
import {isRmlDocument, RML_DOCUMENT_SELECTOR} from './documentUtils';
import {RmlEvalInlayHintsProvider} from './evalInlayHintsProvider';

export function registerLanguageFeatures(
    client: RmlDevClient,
    inlayHintsProvider: RmlEvalInlayHintsProvider
): vscode.Disposable[] {
    const disposables: vscode.Disposable[] = [];

    disposables.push(
        vscode.languages.registerCompletionItemProvider(
            RML_DOCUMENT_SELECTOR,
            new RmlCompletionItemProvider(client),
            '"', '$', '{', '}', '/', ':', '.', '_', '@'
        )
    );

    disposables.push(
        vscode.languages.registerInlayHintsProvider(
            RML_DOCUMENT_SELECTOR,
            inlayHintsProvider
        )
    );

    disposables.push(
        vscode.languages.registerCodeLensProvider(
            RML_DOCUMENT_SELECTOR,
            {
                provideCodeLenses: async (document: vscode.TextDocument): Promise<vscode.CodeLens[]> => {
                    if (!client.isConnected() || !isRmlDocument(document)) {
                        return [];
                    }
                    try {
                        const lenses: any[] = await client.codeLens({
                            textDocument: {uri: document.uri.toString()}
                        });
                        return (lenses || []).map(l => new vscode.CodeLens(
                            new vscode.Range(l.range.start.line, l.range.start.character, l.range.end.line, l.range.end.character),
                            l.command ? {
                                title: l.command.title,
                                command: l.command.command,
                                arguments: l.command.arguments
                            } : undefined
                        ));
                    } catch {
                        return [];
                    }
                }
            }
        )
    );

    return disposables;
}
