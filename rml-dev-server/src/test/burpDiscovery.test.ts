import * as assert from 'assert';
import * as path from 'path';
import * as vscode from 'vscode';
import {findBurpExecutable, getExpectedStorageJarPath} from '../burpDiscovery';

suite('BURP Discovery Tests', () => {
    test('getExpectedStorageJarPath formats versioned path correctly', () => {
        const mockContext = {
            globalStorageUri: vscode.Uri.file('/mock/storage'),
            extension: {packageJSON: {version: '0.1.4'}}
        } as unknown as vscode.ExtensionContext;

        const storagePath = getExpectedStorageJarPath(mockContext);
        assert.strictEqual(storagePath, path.join('/mock/storage', 'server', 'burp-0.1.4.jar'));
    });

    test('findBurpExecutable discovers explicit configured burp.path', async () => {
        const targetJar = path.resolve(__dirname, '../../../target/burp.jar');
        const config = vscode.workspace.getConfiguration('rml');
        await config.update('burp.path', targetJar, vscode.ConfigurationTarget.Global);

        const mockContext = {
            globalStorageUri: vscode.Uri.file('/mock/storage'),
            extension: {packageJSON: {version: '0.1.4'}}
        } as unknown as vscode.ExtensionContext;

        const target = findBurpExecutable(mockContext);
        assert.ok(target, 'Expected BURP target to be found');
        assert.strictEqual(target.type, 'jar');
        assert.strictEqual(target.source, 'config');
        assert.strictEqual(target.path, targetJar);

        // Reset config
        await config.update('burp.path', '', vscode.ConfigurationTarget.Global);
    });

    test('findBurpExecutable auto-discovers workspace target/burp.jar when config is empty', async () => {
        const config = vscode.workspace.getConfiguration('rml');
        await config.update('burp.path', '', vscode.ConfigurationTarget.Global);

        const mockContext = {
            globalStorageUri: vscode.Uri.file('/nonexistent/storage'),
            extension: {packageJSON: {version: '0.1.4'}}
        } as unknown as vscode.ExtensionContext;

        const target = findBurpExecutable(mockContext);
        assert.ok(target, 'Expected workspace BURP target to be auto-discovered');
        assert.strictEqual(target.type, 'jar');
        assert.ok(target.source === 'workspace' || target.source === 'local', `Unexpected source: ${target.source}`);
        assert.ok(target.path.endsWith('.jar'));
    });
});
