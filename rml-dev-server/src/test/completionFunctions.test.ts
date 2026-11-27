import * as assert from 'assert';
import * as vscode from 'vscode';
import {getTestMappingUri, setupTestServer, teardownTestServer, TestContext} from './testHelper';

suite('RML Dev Server Function Execution Completion Test', function () {
    this.timeout(45000);

    let ctx: TestContext;

    setup(async () => {
        ctx = await setupTestServer('Function Completion Test Output');
    });

    teardown(async () => {
        await teardownTestServer(ctx);
    });

    test('Completes function execution parameters and functions without CSV columns', async function () {
        const mappingUri = getTestMappingUri('complete_function/mapping.ttl');
        const doc = await vscode.workspace.openTextDocument(mappingUri);
        const editor = await vscode.window.showTextDocument(doc);

        const paramPos = new vscode.Position(21, 31);
        editor.selection = new vscode.Selection(paramPos, paramPos);

        const completionResult = await vscode.commands.executeCommand<vscode.CompletionList>(
            'vscode.executeCompletionItemProvider',
            doc.uri,
            paramPos
        );
        const completionItems = completionResult ? (Array.isArray(completionResult) ? completionResult : completionResult.items) : [];
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
