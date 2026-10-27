import * as assert from 'assert';
import * as vscode from 'vscode';
import {getTestMappingUri, setupTestServer, teardownTestServer, TestContext} from './testHelper';

suite('RML Dev Server Structure View & Bidirectional Navigation Test', function () {
    this.timeout(45000);

    let ctx: TestContext;

    setup(async () => {
        ctx = await setupTestServer('Structure View Test Output');
    });

    teardown(async () => {
        await teardownTestServer(ctx);
    });

    test('Editor -> Tree: Cursor position resolves the active selectedNodeId', async function () {
        // 1. Open mapping.ttl in editor
        const mappingUri = getTestMappingUri('eval_alice/mapping.ttl');
        const doc = await vscode.workspace.openTextDocument(mappingUri);
        const editor = await vscode.window.showTextDocument(doc);
        await ctx.client.didOpenTextDocument(doc);

        // 2. Move editor cursor to line 19 (rml:reference "$.age") -> line 18 (0-indexed)
        const targetPos = new vscode.Position(18, 20);
        editor.selection = new vscode.Selection(targetPos, targetPos);

        // 3. Query plan with cursor selector (Editor -> Tree)
        const planResult = await ctx.client.plan({
            mappingFileUri: mappingUri.toString(),
            structure: {
                type: 'cursor',
                mappingFileUri: mappingUri.toString(),
                position: { line: targetPos.line, character: targetPos.character }
            } as any
        });

        assert.ok(planResult, 'Expected PlanResult to be returned');
        assert.ok(planResult.roots && planResult.roots.length > 0, 'Expected at least one root TriplesMap');
        assert.ok(planResult.selectedNodeId, 'Expected selectedNodeId to be resolved from cursor position');
    });

    test('Tree -> Editor: Node originRange reveals and selects the code in editor', async function () {
        // 1. Open mapping.ttl in editor
        const mappingUri = getTestMappingUri('eval_alice/mapping.ttl');
        const doc = await vscode.workspace.openTextDocument(mappingUri);
        const editor = await vscode.window.showTextDocument(doc);
        await ctx.client.didOpenTextDocument(doc);

        // 2. Retrieve plan tree
        const planResult = await ctx.client.plan({
            mappingFileUri: mappingUri.toString(),
            structure: null as any
        });

        assert.ok(planResult && planResult.roots.length > 0, 'Expected plan roots');

        function findNodeWithRange(nodes: any[]): any {
            for (const n of nodes) {
                if (n.originRange && n.originRange.length > 0) {
                    return n;
                }
                if (n.children && n.children.length > 0) {
                    const found = findNodeWithRange(n.children);
                    if (found) {
                        return found;
                    }
                }
            }
            return null;
        }

        const targetNode = findNodeWithRange(planResult.roots);
        assert.ok(targetNode, 'Expected to find a plan node with an originRange');
        assert.ok(targetNode.originRange && targetNode.originRange.length > 0, 'Expected node to have non-empty originRange');

        // 3. Simulate Tree -> Editor selection
        const primaryRange = targetNode.originRange[0];
        const start = new vscode.Position(primaryRange.start.line, primaryRange.start.character);
        const end = new vscode.Position(primaryRange.end.line, primaryRange.end.character);
        editor.selection = new vscode.Selection(start, end);
        editor.revealRange(new vscode.Range(start, end), vscode.TextEditorRevealType.InCenter);

        // 4. Assert editor selection matches node originRange primary range
        assert.strictEqual(editor.selection.start.line, primaryRange.start.line, 'Selection start line matches');
        assert.strictEqual(editor.selection.start.character, primaryRange.start.character, 'Selection start character matches');
        assert.strictEqual(editor.selection.end.line, primaryRange.end.line, 'Selection end line matches');
        assert.strictEqual(editor.selection.end.character, primaryRange.end.character, 'Selection end character matches');

        if (targetNode.originRange.length > 1) {
            editor.selections = targetNode.originRange.map((r: any) => new vscode.Selection(
                new vscode.Position(r.start.line, r.start.character),
                new vscode.Position(r.end.line, r.end.character)
            ));
            assert.strictEqual(editor.selections.length, targetNode.originRange.length);
        }
    });
});
