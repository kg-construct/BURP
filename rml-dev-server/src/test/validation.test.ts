import * as assert from 'assert';
import * as vscode from 'vscode';
import {getTestMappingUri, setupTestServer, teardownTestServer, TestContext} from './testHelper';

suite('RML Dev Server Validation & Diagnostics Test', function () {
    this.timeout(45000);

    let ctx: TestContext;

    setup(async () => {
        ctx = await setupTestServer('Validation Test Output');
    });

    teardown(async () => {
        await teardownTestServer(ctx);
    });

    test('Validates mapping and detects unused anonymous RML gather element with warning', async function () {
        // 1. Open validate_unused_gather/mapping.ttl
        const mappingUri = getTestMappingUri('validate_unused_gather/mapping.ttl');
        const doc = await vscode.workspace.openTextDocument(mappingUri);
        await vscode.window.showTextDocument(doc);
        await ctx.client.didOpenTextDocument(doc);

        // 2. Run validation on the document
        const diagnostics = await ctx.client.validate({
            mappingFileUri: mappingUri.toString()
        });

        assert.ok(diagnostics);
        assert.strictEqual(diagnostics.length, 1,
            "Expected one diagnostic received :" + JSON.stringify(diagnostics, null, 2));

        const diag = diagnostics[0];
        assert.strictEqual(diag.rerCodeUri, "https://w3id.org/dre/rer#UnusedRmlElement");
        assert.ok((diag.severity as any) === 2 || (diag.severity as any) === "Warning", `Expected severity Warning (2), got ${diag.severity}`);
        assert.ok(diag.range);
        assert.strictEqual(diag.range.start.line, 12);
        assert.strictEqual(diag.range.start.character, 3);
    });
});
