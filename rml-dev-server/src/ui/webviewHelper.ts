import * as vscode from 'vscode';

export function getWebviewHtml(webview: vscode.Webview, extensionUri: vscode.Uri, viewType: string): string {
    const scriptUri = webview.asWebviewUri(vscode.Uri.joinPath(extensionUri, 'dist', 'webview.js'));
    const stylesUri = webview.asWebviewUri(vscode.Uri.joinPath(extensionUri, 'dist', 'webview.css'));
    const nonce = getNonce();

    return `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src ${webview.cspSource} 'unsafe-inline'; script-src 'nonce-${nonce}'; font-src ${webview.cspSource} data:;">
    <link rel="stylesheet" href="${stylesUri}">
    <title>RML Dev Server</title>
</head>
<body>
    <div id="root" data-view="${viewType}"></div>
    <script nonce="${nonce}">
        window.__VIEW_TYPE__ = "${viewType}";
    </script>
    <script nonce="${nonce}" src="${scriptUri}"></script>
</body>
</html>`;
}

function getNonce(): string {
    let text = '';
    const possible = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    for (let i = 0; i < 32; i++) {
        text += possible.charAt(Math.floor(Math.random() * possible.length));
    }
    return text;
}

export async function revealRangeInEditor(
    fileUri: string,
    range: { start: { line: number; character: number }; end: { line: number; character: number } },
    ranges?: { start: { line: number; character: number }; end: { line: number; character: number } }[]
): Promise<void> {
    const doc = await vscode.workspace.openTextDocument(vscode.Uri.parse(fileUri));
    const start = new vscode.Position(range.start.line, range.start.character);
    const end = new vscode.Position(range.end.line, range.end.character);
    const primarySelection = new vscode.Selection(start, end);
    const editor = await vscode.window.showTextDocument(doc, {
        viewColumn: vscode.ViewColumn.One,
        selection: primarySelection
    });
    if (ranges && ranges.length > 0) {
        editor.selections = ranges.map(
            (r) =>
                new vscode.Selection(
                    new vscode.Position(r.start.line, r.start.character),
                    new vscode.Position(r.end.line, r.end.character)
                )
        );
    } else {
        editor.selection = primarySelection;
    }
    editor.revealRange(primarySelection, vscode.TextEditorRevealType.InCenterIfOutsideViewport);
}

