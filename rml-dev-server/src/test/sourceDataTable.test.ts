import * as assert from 'assert';
import * as vscode from 'vscode';
import {getTestMappingUri, setupTestServer, teardownTestServer, TestContext} from './testHelper';

suite('RML Dev Server Logical View Table Preview Test', function () {
    this.timeout(45000);

    let ctx: TestContext;

    setup(async () => {
        ctx = await setupTestServer('Logical View Preview Test Output');
    });

    teardown(async () => {
        await teardownTestServer(ctx);
    });

    test('Generates CodeLens for logical views and fetches windowed table preview', async function () {
        const mappingUri = getTestMappingUri('eval_logicalview/mapping.ttl');
        const doc = await vscode.workspace.openTextDocument(mappingUri);
        await vscode.window.showTextDocument(doc);
        await ctx.client.didOpenTextDocument(doc);

        // 1. CodeLens verification
        const lenses = await ctx.client.codeLens({
            textDocument: { uri: doc.uri.toString() }
        });

        assert.ok(lenses, 'Expected CodeLenses to be returned');
        assert.ok(lenses.length >= 2, `Expected at least 2 CodeLenses, got ${lenses.length}`);

        const hasTableLens = lenses.some(l => l.command && l.command.title.includes('View Logical View Data'));
        assert.ok(hasTableLens, 'Expected at least one View Logical View Data CodeLens');

        // 2. previewLogicalView on :jsonView with pagination
        const previewPage1 = await ctx.client.getSourceData({
            structure: {
                type: 'iri',
                mappingFileUri: doc.uri.toString(),
                iri: 'http://example.org/jsonView'
            } as any,
            offset: 0,
            limit: 1,
            dataContext: { type: 'all' }
        });

        assert.ok(previewPage1, 'Expected previewPage1 result');
        assert.strictEqual(previewPage1.format, 'table');
        assert.strictEqual(previewPage1.totalItems, 2);
        assert.strictEqual(previewPage1.totalExact, true);
        assert.strictEqual(previewPage1.hasMore, true);
        assert.ok(previewPage1.table, 'Expected table data');
        assert.strictEqual(previewPage1.table.rows.length, 1);
        assert.ok(previewPage1.table.headers.includes('name'));

        const previewPage2 = await ctx.client.getSourceData({
            structure: {
                type: 'iri',
                mappingFileUri: doc.uri.toString(),
                iri: 'http://example.org/jsonView'
            } as any,
            offset: 1,
            limit: 1,
            dataContext: { type: 'all' }
        });

        assert.ok(previewPage2.table, 'Expected table data');
        assert.strictEqual(previewPage2.table.rows.length, 1);
        assert.strictEqual(previewPage2.hasMore, false);

        // 3. previewLogicalView on :jsonSource (document format)
        const previewJsonSource = await ctx.client.getSourceData({
            structure: {
                type: 'iri',
                mappingFileUri: doc.uri.toString(),
                iri: 'http://example.org/jsonSource'
            } as any,
            offset: 0,
            limit: 10,
            dataContext: { type: 'all' }
        });

        assert.ok(previewJsonSource, 'Expected previewJsonSource result');
        assert.strictEqual(previewJsonSource.format, 'json');
        assert.ok(previewJsonSource.document, 'Expected document data');
        assert.strictEqual(previewJsonSource.document.language, 'json');
        assert.ok(previewJsonSource.document.items.length > 0);

        // 4. previewLogicalView on :csvView with join
        const previewCsv = await ctx.client.getSourceData({
            structure: {
                type: 'cursor',
                mappingFileUri: doc.uri.toString(),
                position: { line: 37, character: 10 }
            } as any,
            offset: 0,
            limit: 10,
            dataContext: { type: 'all' }
        });

        assert.strictEqual(previewCsv.format, 'table');
        assert.strictEqual(previewCsv.totalItems, 2);
        assert.ok(previewCsv.table, 'Expected table data');
        assert.ok(previewCsv.table.headers.includes('item_type'));
        assert.strictEqual(previewCsv.table.rows.length, 2);
    });
});
