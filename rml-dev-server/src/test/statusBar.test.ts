import * as assert from 'assert';
import * as vscode from 'vscode';
import {StatusBarManager} from '../statusBar';

suite('StatusBarManager Tooltip & Commands Tests', () => {
    let statusBar: StatusBarManager;

    setup(() => {
        statusBar = new StatusBarManager();
    });

    teardown(() => {
        statusBar.dispose();
    });

    test('initial state is disconnected with start and settings links', () => {
        assert.ok(statusBar.getText().includes('Disconnected'));
        const tooltip = statusBar.getTooltip();
        assert.ok(tooltip instanceof vscode.MarkdownString);
        assert.strictEqual(tooltip.isTrusted, true);
        assert.ok(tooltip.value.includes('command:rml.startServer'));
        assert.ok(tooltip.value.includes('command:rml.openSettings'));
        assert.ok(tooltip.value.includes('command:rml.showOutput'));
    });

    test('connected state contains restart, clearCache, and settings command links', () => {
        statusBar.updateStatus('connected', 8080, 15);
        assert.ok(statusBar.getText().includes('Connected:8080'));
        assert.ok(statusBar.getText().includes('(15ms)'));

        const tooltip = statusBar.getTooltip();
        assert.ok(tooltip instanceof vscode.MarkdownString);
        assert.strictEqual(tooltip.isTrusted, true);
        assert.strictEqual(tooltip.supportThemeIcons, true);

        // Core requested links
        assert.ok(tooltip.value.includes('command:rml.restartServer'), 'Expected restart link');
        assert.ok(tooltip.value.includes('command:rml.clearCache'), 'Expected clearCache link');
        assert.ok(tooltip.value.includes('command:rml.openSettings'), 'Expected settings link');

        // Additional utility links
        assert.ok(tooltip.value.includes('command:rml.healthCheck'), 'Expected health check link');
        assert.ok(tooltip.value.includes('command:rml.showOutput'), 'Expected show output link');
        assert.ok(tooltip.value.includes('command:rml.stopServer'), 'Expected stop server link');
    });

    test('starting and error states offer appropriate navigation links', () => {
        statusBar.updateStatus('starting');
        assert.ok(statusBar.getText().includes('Starting...'));
        let tooltip = statusBar.getTooltip();
        assert.ok(tooltip instanceof vscode.MarkdownString);
        assert.ok(tooltip.value.includes('command:rml.showOutput'));
        assert.ok(tooltip.value.includes('command:rml.openSettings'));

        statusBar.updateStatus('error');
        assert.ok(statusBar.getText().includes('Error'));
        tooltip = statusBar.getTooltip();
        assert.ok(tooltip instanceof vscode.MarkdownString);
        assert.ok(tooltip.value.includes('command:rml.restartServer'));
        assert.ok(tooltip.value.includes('command:rml.openSettings'));
    });
});
