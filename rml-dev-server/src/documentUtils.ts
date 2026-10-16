import * as vscode from 'vscode';

export const RML_DOCUMENT_SELECTOR: vscode.DocumentSelector = [
    {language: 'turtle', scheme: 'file'},
    {language: 'rml', scheme: 'file'},
    {pattern: '**/*.ttl', scheme: 'file'},
    {pattern: '**/*.rml.ttl', scheme: 'file'},
    {pattern: '**/*.rml', scheme: 'file'}
];

export function isRmlDocument(document: vscode.TextDocument): boolean {
    return (
        document.languageId === 'turtle' ||
        document.languageId === 'rml' ||
        document.fileName.endsWith('.ttl') ||
        document.fileName.endsWith('.rml.ttl') ||
        document.fileName.endsWith('.rml')
    );
}
