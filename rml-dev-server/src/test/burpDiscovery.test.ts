import * as assert from 'assert';
import * as path from 'path';
import * as vscode from 'vscode';
import {findBurpExecutable} from '../burpDiscovery';

suite('BURP Discovery Tests', () => {
    test('findBurpExecutable returns null when storage and config are empty', () => {
        const mockContext = {
            globalStorageUri: vscode.Uri.file('/nonexistent/mock/storage'),
            extension: {packageJSON: {version: '0.1.4', serverVersion: '0.1.4'}}
        } as unknown as vscode.ExtensionContext;

        const target = findBurpExecutable(mockContext);
        assert.strictEqual(target, null);
    });

    test('findBurpExecutable discovers explicit configured burp.path', async () => {
        const targetJar = path.resolve(__dirname, '../../../target/burp.jar');
        const config = vscode.workspace.getConfiguration('rml');
        await config.update('burp.path', targetJar, vscode.ConfigurationTarget.Global);

        const mockContext = {
            globalStorageUri: vscode.Uri.file('/mock/storage'),
            extension: {packageJSON: {version: '0.1.4', serverVersion: '0.1.4'}}
        } as unknown as vscode.ExtensionContext;

        const target = findBurpExecutable(mockContext);
        assert.ok(target, 'Expected BURP target to be found');
        assert.strictEqual(target.type, 'jar');
        assert.strictEqual(target.source, 'config');
        assert.strictEqual(target.path, targetJar);

        // Reset config
        await config.update('burp.path', '', vscode.ConfigurationTarget.Global);
    });
});
