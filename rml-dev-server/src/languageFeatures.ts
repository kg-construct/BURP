import * as vscode from 'vscode';
import {RmlDevClient} from './client';
import {RML_DOCUMENT_SELECTOR} from './documentUtils';
import {RmlEvalInlayHintsProvider} from './evalInlayHintsProvider';

export function registerLanguageFeatures(
    _client: RmlDevClient,
    inlayHintsProvider: RmlEvalInlayHintsProvider
): vscode.Disposable[] {
    return [
        vscode.languages.registerInlayHintsProvider(
            RML_DOCUMENT_SELECTOR,
            inlayHintsProvider
        )
    ];
}
