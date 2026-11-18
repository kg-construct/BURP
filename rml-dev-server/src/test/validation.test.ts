import * as assert from 'assert';
import * as vscode from 'vscode';
import {RmlDevClient} from '../client';
import {getTestMappingUri, setupTestServer, teardownTestServer, TestContext} from './testHelper';

function waitForDiagnostics(client: RmlDevClient, uri: vscode.Uri, timeoutMs = 15000): Promise<readonly vscode.Diagnostic[]> {
    const existing = client.diagnostics?.get(uri);
    if (existing && existing.length > 0) {
        return Promise.resolve(existing);
    }
    return new Promise((resolve, reject) => {
        let timer: NodeJS.Timeout;
        const disposable = vscode.languages.onDidChangeDiagnostics((e) => {
            if (e.uris.some(u => u.toString() === uri.toString())) {
                const diags = client.diagnostics?.get(uri);
                if (diags && diags.length > 0) {
                    clearTimeout(timer);
                    disposable.dispose();
                    resolve(diags);
                }
            }
        });
        timer = setTimeout(() => {
            disposable.dispose();
            const diags = client.diagnostics?.get(uri);
            if (diags && diags.length > 0) {
                resolve(diags);
            } else {
                reject(new Error(`Timed out after ${timeoutMs}ms waiting for diagnostics on ${uri.toString()}`));
            }
        }, timeoutMs);
    });
}

suite('RML Dev Server Validation & Diagnostics Test', function () {
    this.timeout(45000);

    let ctx: TestContext;

    setup(async () => {
        ctx = await setupTestServer('Validation Test Output');
    });

    teardown(async () => {
        await teardownTestServer(ctx);
    });

    test('Validates mapping and detects unused anonymous RML gather element with warning via standard LSP', async function () {
        // 1. Open validate_unused_gather/mapping.ttl
        const mappingUri = getTestMappingUri('validate_unused_gather/mapping.ttl');
        const doc = await vscode.workspace.openTextDocument(mappingUri);
        await vscode.languages.setTextDocumentLanguage(doc, 'turtle');
        await vscode.window.showTextDocument(doc);

        // 2. Await diagnostics pushed by language server via standard LSP (textDocument/publishDiagnostics)
        const diagnostics = await waitForDiagnostics(ctx.client, mappingUri);

        assert.ok(diagnostics);
        assert.strictEqual(diagnostics.length, 1,
            "Expected one diagnostic received :" + JSON.stringify(diagnostics, null, 2));

        const diag = diagnostics[0];
        const rerCode = typeof diag.code === 'object' && diag.code ? String(diag.code.value) : String(diag.code || '');
        assert.strictEqual(rerCode, "https://w3id.org/dre/rer#UnusedRmlElement");
        assert.strictEqual(diag.severity, vscode.DiagnosticSeverity.Warning);
        assert.ok(diag.range);
        assert.strictEqual(diag.range.start.line, 12);
        assert.strictEqual(diag.range.start.character, 3);
    });
});
