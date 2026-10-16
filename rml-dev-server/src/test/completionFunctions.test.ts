import * as assert from 'assert';
import * as vscode from 'vscode';
import {RmlCompletionItemProvider} from '../completionProvider';
import {getTestMappingUri, setupTestServer, teardownTestServer, TestContext} from './testHelper';

suite('RML Dev Server Function Execution Completion Test', function () {
    this.timeout(45000);

    let ctx: TestContext;
    let providerDisposable: vscode.Disposable;

    setup(async () => {
        ctx = await setupTestServer('Function Completion Test Output');
    });

    teardown(async () => {
        if (providerDisposable) {
            providerDisposable.dispose();
        }
        await teardownTestServer(ctx);
    });

    test('Completes function execution parameters and functions without CSV columns', async function () {
        const completionProvider = new RmlCompletionItemProvider(ctx.client);
        providerDisposable = vscode.languages.registerCompletionItemProvider(
            [{ language: 'turtle' }, { pattern: '**/*.ttl' }],
            completionProvider,
            '"', '$', '{', '}', '/', ':', '.', '_', '@'
        );

        const mappingUri = getTestMappingUri('complete_function/mapping.ttl');
        const doc = await vscode.workspace.openTextDocument(mappingUri);
        const editor = await vscode.window.showTextDocument(doc);
        await ctx.client.didOpenTextDocument(doc);

        const paramPos = new vscode.Position(21, 31);
        editor.selection = new vscode.Selection(paramPos, paramPos);

        const completionResult = await completionProvider.provideCompletionItems(
            doc,
            paramPos,
            new vscode.CancellationTokenSource().token
        );
        const completionItems = Array.isArray(completionResult) ? completionResult : (completionResult?.items || []);
        assert.ok(completionItems.length > 0, 'Expected completion items to be returned');
        const paramLabels = completionItems.map(item => typeof item.label === 'string' ? item.label : item.label.label);

        assert.ok(paramLabels.includes('idlab-fn:str') || paramLabels.includes('idlab-fn:_str'), 
            'Expected idlab-fn:str parameter completion');

        assert.ok(!paramLabels.includes('idlab-fn:otherStr') && !paramLabels.includes('idlab-fn:_otherStr'), 
            'Should NOT include already provided parameter "idlab-fn:otherStr"');

        assert.ok(!paramLabels.includes('idlab-fn:delimiter') && !paramLabels.includes('idlab-fn:_delimiter'), 
            'Should NOT include already provided parameter "idlab-fn:delimiter"');

        assert.ok(!paramLabels.includes('$.firstName'), 
            'Should NOT include logical source references.');
    });
});
