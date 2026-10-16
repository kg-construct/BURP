import * as assert from 'assert';
import * as vscode from 'vscode';
import {RmlEvalInlayHintsProvider} from '../evalInlayHintsProvider';
import {getTestMappingUri, setupTestServer, teardownTestServer, TestContext} from './testHelper';

suite('RML Dev Server Inlay Hints Test', function () {
    this.timeout(45000);

    let ctx: TestContext;
    let providerDisposable: vscode.Disposable;
    let hintsProvider: RmlEvalInlayHintsProvider;

    setup(async () => {
        ctx = await setupTestServer('Inlay Hints Test Output');
        hintsProvider = new RmlEvalInlayHintsProvider(ctx.client);
    });

    teardown(async () => {
        if (providerDisposable) {
            providerDisposable.dispose();
        }
        await teardownTestServer(ctx);
    });

    test('Generates inline hints for references, templates, function executions, and function arguments', async function () {
        // 1. Register inlay hints provider
        providerDisposable = vscode.languages.registerInlayHintsProvider(
            [{ language: 'turtle' }, { pattern: '**/*.ttl' }],
            hintsProvider
        );

        // 2. Open BURPFNMLTC-CSV-Concat/mapping.ttl in an editor
        const mappingUri = getTestMappingUri('BURPFNMLTC-CSV-Concat/mapping.ttl');
        const doc = await vscode.workspace.openTextDocument(mappingUri);
        await vscode.window.showTextDocument(doc);

        await ctx.client.didOpenTextDocument(doc);

        // 5. Execute Inlay Hints Provider on the entire document range
        const fullRange = new vscode.Range(new vscode.Position(0, 0), new vscode.Position(doc.lineCount, 0));
        const cancellationSource = new vscode.CancellationTokenSource();
        const hints = await hintsProvider.provideInlayHints(doc, fullRange, cancellationSource.token);

        assert.ok(hints, 'Expected inlay hints array to be returned');
        assert.ok(hints.length > 0, `Expected at least one inlay hint, got ${hints.length}`);

        const hintLabels = hints.map(h => typeof h.label === 'string' ? h.label : h.label.map(p => p.value).join(''));

        // Check 1: Template reference hint exists ("1" from {Id})
        const hasTemplateRefHint = hintLabels.some(l => l.includes('1') || l.includes('"1"'));
        assert.ok(hasTemplateRefHint, `Expected template reference hint ("1") in hints: ${hintLabels.join(', ')}`);

        // Check 2: Full Template output hint exists (<http://example.com/1>)
        const hasFullTemplateHint = hintLabels.some(l => l.includes('http://example.com/1'));
        assert.ok(hasFullTemplateHint, `Expected full template hint (<http://example.com/1>) in hints: ${hintLabels.join(', ')}`);

        // Check 3: Reference / argument 1 hint exists ("Gaston" from Name)
        const hasGastonArg = hintLabels.some(l => l.includes('Gaston') || l.includes('"Gaston"'));
        assert.ok(hasGastonArg, `Expected function argument hint ("Gaston") in hints: ${hintLabels.join(', ')}`);

        // Check 4: Reference / argument 2 hint exists ("A" from Class)
        const hasClassArg = hintLabels.some(l => l.includes('"A"') || l.includes('A'));
        assert.ok(hasClassArg, `Expected function argument hint ("A") in hints: ${hintLabels.join(', ')}`);

        // Check 5: Function execution result hint exists ("Gaston - A" from concat)
        const hasConcatResult = hintLabels.some(l => l.includes('Gaston - A'));
        assert.ok(hasConcatResult, `Expected function execution result hint ("Gaston - A") in hints: ${hintLabels.join(', ')}`);

        // 6. Test dynamic data context update (Custom Reference Values: Id="10", Name="Venus", Class="Pro")
        hintsProvider.setDataContext({
            type: 'referenceValues',
            referenceValues: { Id: '10', Name: 'Venus', Class: 'Pro' }
        } as any);

        const customHints = await hintsProvider.provideInlayHints(doc, fullRange, cancellationSource.token);
        assert.ok(customHints && customHints.length > 0, 'Expected custom hints');

        const customLabels = customHints.map(h => typeof h.label === 'string' ? h.label : h.label.map(p => p.value).join(''));
        const hasVenusArg = customLabels.some(l => l.includes('Venus') || l.includes('"Venus"'));
        assert.ok(hasVenusArg, `Expected custom hint ("Venus") in hints: ${customLabels.join(', ')}`);

        const hasVenusTemplate = customLabels.some(l => l.includes('http://example.com/10'));
        assert.ok(hasVenusTemplate, `Expected custom template hint (<http://example.com/10>) in hints: ${customLabels.join(', ')}`);

        const hasVenusConcat = customLabels.some(l => l.includes('Venus - Pro'));
        assert.ok(hasVenusConcat, `Expected custom function result hint ("Venus - Pro") in hints: ${customLabels.join(', ')}`);

        const config = vscode.workspace.getConfiguration('rml');
        await config.update('inlineHints.enableEvaluation', false, vscode.ConfigurationTarget.Global);
        await ctx.client.didChangeConfiguration({
            settings: { rml: { inlineHints: { enableEvaluation: false } } }
        });
        hintsProvider.refresh();

        const staticHints = await hintsProvider.provideInlayHints(doc, fullRange, cancellationSource.token);
        assert.ok(staticHints && staticHints.length > 0, 'Expected static hints when inline evaluation is disabled');
        const staticLabels = staticHints.map(h => typeof h.label === 'string' ? h.label : h.label.map(p => p.value).join(''));
        assert.ok(staticLabels.some(l => l.includes('input string (xsd:string)')), `Expected static function parameter hint in: ${staticLabels.join(', ')}`);
        assert.ok(!staticLabels.some(l => l.includes('Venus') || l.includes('Gaston') || l.includes('http://example.com/10')), `Expected no evaluated values in: ${staticLabels.join(', ')}`);
    });
});
