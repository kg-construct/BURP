import * as assert from 'assert';
import * as vscode from 'vscode';
import {RmlCompletionItemProvider} from '../completionProvider';
import {getTestMappingUri, setupTestServer, teardownTestServer, TestContext} from './testHelper';

suite('RML Dev Server Reference Completion Test', function () {
    this.timeout(45000);

    let ctx: TestContext;
    let providerDisposable: vscode.Disposable;

    setup(async () => {
        ctx = await setupTestServer('Completion Test Output');
    });

    teardown(async () => {
        if (providerDisposable) {
            providerDisposable.dispose();
        }
        await teardownTestServer(ctx);
    });

    test('Opens file, positions cursor, triggers completion, and tests completion application result', async function () {
        // 1. Register completion provider for testing
        const completionProvider = new RmlCompletionItemProvider(ctx.client);
        providerDisposable = vscode.languages.registerCompletionItemProvider(
            [{ language: 'turtle' }, { pattern: '**/*.ttl' }],
            completionProvider,
            '"', '$', '{', '}', '/', ':', '.', '_', '@'
        );

        // 2. Open the complete_reference_schema/mapping.ttl file in an editor
        const mappingUri = getTestMappingUri('complete_reference_schema/mapping.ttl');
        const doc = await vscode.workspace.openTextDocument(mappingUri);
        const editor = await vscode.window.showTextDocument(doc);

        await ctx.client.didOpenTextDocument(doc);

        // Test 1: Position cursor inside rml:reference "$.fake" (line 23, character 28 right after '$.f')
        const targetPos = new vscode.Position(23, 28);
        editor.selection = new vscode.Selection(targetPos, targetPos);

        const completionList = await vscode.commands.executeCommand<vscode.CompletionList>(
            'vscode.executeCompletionItemProvider',
            doc.uri,
            targetPos
        );

        assert.ok(completionList, 'Expected completion list to be returned by VS Code');
        assert.ok(completionList.items && completionList.items.length > 0, 'Expected items in completion list');

        const firstNameItem = completionList.items.find(item => {
            const labelStr = typeof item.label === 'string' ? item.label : item.label.label;
            return labelStr === '$.firstName';
        });
        assert.ok(firstNameItem, 'Expected $.firstName item');
        assert.ok(firstNameItem.detail?.includes('Alice'), `Expected example value Alice in completion item detail, got: ${firstNameItem.detail}`);
        assert.ok(firstNameItem.detail?.includes('JSONPath'), 'Expected JSONPath formulation in detail');

        // Test 2: Apply the completion edit to the document and verify the exact resulting text
        const insertText = typeof firstNameItem.insertText === 'string' ? firstNameItem.insertText : firstNameItem.label;
        const itemRange = firstNameItem.range instanceof vscode.Range
            ? firstNameItem.range
            : (firstNameItem.range as any)?.inserting || new vscode.Range(targetPos, targetPos);

        await editor.edit(editBuilder => {
            editBuilder.replace(itemRange, insertText as string);
        });

        const line23Text = doc.lineAt(23).text;
        assert.strictEqual(
            line23Text.trim(),
            'rml:reference "$.firstName"',
            `Expected 'rml:reference "$.firstName"' after completion edit, but got: '${line23Text}'`
        );
    });
});
