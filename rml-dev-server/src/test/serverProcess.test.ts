import * as assert from 'assert';
import * as path from 'path';
import * as vscode from 'vscode';
import {ServerProcess} from '../serverProcess';

suite('ServerProcess Port & Spawn Configuration Tests', () => {
    const outputChannel = vscode.window.createOutputChannel('Test ServerProcess');

    teardown(async () => {
        const config = vscode.workspace.getConfiguration('rml');
        await config.update('server.spawnProcess', undefined, vscode.ConfigurationTarget.Global);
        await config.update('server.port', undefined, vscode.ConfigurationTarget.Global);
        await config.update('burp.path', undefined, vscode.ConfigurationTarget.Global);
    });

    test('resolveBurpExecution always uses random port flag (-d) and ignores server.port', async () => {
        const jarPath = path.resolve(__dirname, '../../../target/burp.jar');
        const config = vscode.workspace.getConfiguration('rml');
        await config.update('burp.path', jarPath, vscode.ConfigurationTarget.Global);
        await config.update('server.port', 9999, vscode.ConfigurationTarget.Global);
        await config.update('server.spawnProcess', true, vscode.ConfigurationTarget.Global);

        const serverProcess = new ServerProcess(outputChannel);
        const execution = serverProcess.resolveBurpExecution();

        assert.ok(execution.args.includes('-d'), 'Expected -d in args');
        assert.ok(!execution.args.includes('-d=9999'), 'Did not expect -d=9999 in args');
        assert.ok(!execution.args.some((arg) => arg.startsWith('-d=')), 'Did not expect any fixed port assignment');
    });

    test('start() uses server.port when spawnProcess is false', async () => {
        const config = vscode.workspace.getConfiguration('rml');
        await config.update('server.spawnProcess', false, vscode.ConfigurationTarget.Global);
        await config.update('server.port', 8888, vscode.ConfigurationTarget.Global);

        const serverProcess = new ServerProcess(outputChannel);
        const port = await serverProcess.start();

        assert.strictEqual(port, 8888);
        assert.strictEqual(serverProcess.getPort(), 8888);
        assert.strictEqual(serverProcess.isRunning(), false, 'Expected no process spawned when spawnProcess=false');
    });

    test('start() throws when spawnProcess is false and server.port is not configured (> 0)', async () => {
        const config = vscode.workspace.getConfiguration('rml');
        await config.update('server.spawnProcess', false, vscode.ConfigurationTarget.Global);
        await config.update('server.port', 0, vscode.ConfigurationTarget.Global);

        const serverProcess = new ServerProcess(outputChannel);
        await assert.rejects(
            async () => {
                await serverProcess.start();
            },
            /Setting 'rml.server.spawnProcess' is false, but 'rml.server.port' is not configured/
        );
    });
});
