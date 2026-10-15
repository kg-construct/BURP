import * as path from 'path';
import * as vscode from 'vscode';
import {RmlDevClient} from '../client';
import {ServerProcess} from '../serverProcess';

export interface TestContext {
    serverProcess: ServerProcess;
    client: RmlDevClient;
    outputChannel: vscode.OutputChannel;
    port: number;
}

/**
 * Returns the absolute path to a test mapping file located under `src/test/resources/burp/`.
 * Example: `getTestMappingPath('eval_alice/mapping.ttl')`
 */
export function getTestMappingPath(relativeSubPath: string): string {
    return path.resolve(__dirname, '../../../src/test/resources/burp', relativeSubPath);
}

/**
 * Returns the absolute URI for a test mapping file located under `src/test/resources/burp/`.
 */
export function getTestMappingUri(relativeSubPath: string): vscode.Uri {
    return vscode.Uri.file(getTestMappingPath(relativeSubPath));
}

/**
 * Configures the VS Code settings, starts the BURP dev server, connects the client, and returns the TestContext.
 */
export async function setupTestServer(outputChannelName: string = 'Test Output'): Promise<TestContext> {
    const jarPath = path.resolve(__dirname, '../../../target/burp.jar');
    const config = vscode.workspace.getConfiguration('rml');
    await config.update('burp.path', jarPath, vscode.ConfigurationTarget.Global);
    await config.update('java.path', 'java', vscode.ConfigurationTarget.Global);
    await config.update('server.port', 0, vscode.ConfigurationTarget.Global);
    await config.update('inlineHints.enableEvaluation', true, vscode.ConfigurationTarget.Global);

    const outputChannel = vscode.window.createOutputChannel(outputChannelName);
    const serverProcess = new ServerProcess(outputChannel);
    const client = new RmlDevClient(outputChannel);

    const port = await serverProcess.start();
    await client.connect(port);

    return {
        serverProcess,
        client,
        outputChannel,
        port
    };
}

/**
 * Disconnects the client, stops the server process, and disposes the output channel.
 */
export async function teardownTestServer(context: TestContext | null | undefined): Promise<void> {
    if (!context) {
        return;
    }
    if (context.client.isConnected()) {
        await context.client.disconnect();
    }
    await context.serverProcess.stop();
    context.outputChannel.dispose();
}
