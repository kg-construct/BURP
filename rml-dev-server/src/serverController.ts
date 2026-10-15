import * as vscode from 'vscode';
import {findBurpExecutable} from './burpDiscovery';
import {downloadBurpServer} from './burpDownloader';
import {RmlDevClient} from './client';
import {getConfiguration} from './config';
import {findCompatibleJavas} from './javaDiscovery';
import {downloadJre} from './javaDownloader';
import {JavaError, JavaErrorCode, ServerProcess} from './serverProcess';
import {StatusBarManager} from './statusBar';

export interface ServerSyncCallbacks {
    onConnected?: (port: number) => Promise<void> | void;
}

export class ServerController implements vscode.Disposable {
    private readonly disposables: vscode.Disposable[] = [];

    constructor(
        private readonly context: vscode.ExtensionContext,
        private readonly serverProcess: ServerProcess,
        private readonly client: RmlDevClient,
        private readonly statusBar: StatusBarManager,
        private readonly outputChannel: vscode.OutputChannel,
        private readonly callbacks?: ServerSyncCallbacks
    ) {
        this.serverProcess.onExit(() => {
            this.statusBar.updateStatus('disconnected');
        });
        this.registerCommands();
    }

    public async startOrConnect(): Promise<boolean> {
        const config = getConfiguration();

        if (config.spawnProcess) {
            const target = findBurpExecutable(this.context);
            if (!target) {
                this.statusBar.updateStatus('disconnected');
                const choice = await vscode.window.showInformationMessage(
                    'RML Dev Server requires the BURP engine. Would you like to download it from GitHub Releases?',
                    'Download',
                    'Configure Path',
                    'Cancel'
                );
                if (choice === 'Download') {
                    try {
                        await downloadBurpServer(this.context, this.outputChannel);
                        return this.startOrConnect();
                    } catch (downloadErr: any) {
                        vscode.window.showErrorMessage(`Failed to download BURP Dev Server: ${downloadErr.message}`);
                        return false;
                    }
                } else if (choice === 'Configure Path') {
                    vscode.commands.executeCommand('workbench.action.openSettings', 'rml.burp.path');
                    return false;
                }
                return false;
            }
        }

        try {
            this.statusBar.updateStatus('starting');
            const port = await this.serverProcess.start();
            await this.client.connect(port);
            this.statusBar.updateStatus('connected', port);
            this.outputChannel.appendLine(`[ServerController] Connected to BURP Dev Server on port ${port}.`);

            if (this.callbacks?.onConnected) {
                await this.callbacks.onConnected(port);
            }
            return true;
        } catch (err: any) {
            this.statusBar.updateStatus('error');
            this.outputChannel.appendLine(`[ServerController] Failed to start/connect server: ${err.message}`);

            if (err instanceof JavaError) {
                const message = err.code === JavaErrorCode.MISSING
                    ? 'RML Dev Server requires Java 21+ but no Java executable was found.'
                    : `RML Dev Server requires Java 21+, but found Java ${err.version}.`;

                vscode.window.showErrorMessage(message, 'Configure Java', 'Cancel').then(async (choice) => {
                    if (choice === 'Configure Java') {
                        await this.showJavaSelectionQuickPick();
                    }
                });
                return false;
            }

            const actions = ['Open Settings', 'Show Logs', 'Retry'];
            vscode.window.showErrorMessage(`RML Dev Server Error: ${err.message}`, ...actions).then(async (choice) => {
                if (choice === 'Open Settings') {
                    vscode.commands.executeCommand('workbench.action.openSettings', 'rml');
                } else if (choice === 'Show Logs') {
                    this.outputChannel.show();
                } else if (choice === 'Retry') {
                    this.startOrConnect();
                }
            });
            return false;
        }
    }

    public async stop(): Promise<void> {
        await this.client.disconnect();
        await this.serverProcess.stop();
        this.statusBar.updateStatus('disconnected');
        this.outputChannel.appendLine('[ServerController] Server stopped.');
    }

    public async restart(): Promise<void> {
        await this.stop();
        await this.startOrConnect();
    }

    public async healthCheck(): Promise<void> {
        if (!this.client.isConnected()) {
            const config = getConfiguration();
            const connectOnly = config.serverPort !== 0 && !config.spawnProcess;
            const message = connectOnly
                ? `RML Dev Server is not connected. Would you like to connect to port ${config.serverPort}?`
                : 'RML Dev Server is not connected. Would you like to start it?';
            const actionLabel = connectOnly ? 'Connect' : 'Start Server';
            const action = await vscode.window.showWarningMessage(message, actionLabel, 'Cancel');
            if (action === actionLabel) {
                const started = await this.startOrConnect();
                if (!started) {
                    return;
                }
            } else {
                return;
            }
        }

        const currentPort = this.serverProcess.getPort() || getConfiguration().serverPort || 0;
        if (currentPort === 0) {
            vscode.window.showErrorMessage('RML Dev Server port is not determined.');
            return;
        }

        const health = await this.client.healthCheck(currentPort);
        if (health.ok) {
            vscode.window.showInformationMessage(
                `RML Dev Server is healthy on port ${health.serverPort} (${health.latencyMs}ms latency).`
            );
        } else {
            const action = await vscode.window.showErrorMessage(
                `RML Dev Server Health Check Failed: ${health.message}`,
                'Restart Server',
                'Show Logs'
            );
            if (action === 'Restart Server') {
                await this.startOrConnect();
            } else if (action === 'Show Logs') {
                this.outputChannel.show();
            }
        }
    }

    public async showJavaSelectionQuickPick(): Promise<void> {
        let javas: any[] = [];
        try {
            javas = await findCompatibleJavas(this.context);
        } catch {
            // ignore scan errors
        }

        const items: vscode.QuickPickItem[] = [];
        for (const java of javas) {
            items.push({
                label: `$(file-binary) Use Java ${java.version}`,
                description: java.executable,
                detail: java.description
            });
        }
        items.push({
            label: '$(cloud-download) Download & Auto-Install JRE 25',
            description: 'Eclipse Temurin',
            detail: 'Automatically download and configure a fresh JRE in VS Code global storage'
        });
        items.push({
            label: '$(settings-gear) Set Java Path manually...',
            detail: "Open VS Code settings to specify 'rml.java.path'"
        });

        const selected = await vscode.window.showQuickPick(items, {
            placeHolder: 'Select a Java 21+ Runtime for the RML Dev Server',
            ignoreFocusOut: true
        });

        if (!selected) {
            return;
        }

        if (selected.label.includes('Set Java Path manually')) {
            vscode.commands.executeCommand('workbench.action.openSettings', 'rml.java.path');
        } else if (selected.label.includes('Download & Auto-Install')) {
            try {
                await downloadJre(this.context, this.outputChannel);
                this.startOrConnect();
            } catch (downloadErr: any) {
                vscode.window.showErrorMessage(`Failed to install JRE: ${downloadErr.message}`);
                this.outputChannel.appendLine(`[ServerController] JRE Download Error: ${downloadErr.message}`);
            }
        } else {
            const executable = selected.description!;
            const config = vscode.workspace.getConfiguration('rml');
            await config.update('java.path', executable, vscode.ConfigurationTarget.Global);
            this.outputChannel.appendLine(`[ServerController] Configured existing Java runtime: ${executable}`);
            this.startOrConnect();
        }
    }

    private registerCommands(): void {
        this.disposables.push(
            vscode.commands.registerCommand('rml-dev-server.startServer', () => this.startOrConnect()),
            vscode.commands.registerCommand('rml-dev-server.stopServer', async () => {
                await this.stop();
                vscode.window.showInformationMessage('RML Dev Server stopped.');
            }),
            vscode.commands.registerCommand('rml-dev-server.restartServer', () => this.restart()),
            vscode.commands.registerCommand('rml-dev-server.healthCheck', () => this.healthCheck()),
            vscode.commands.registerCommand('rml-dev-server.showOutput', () => this.outputChannel.show()),
            vscode.commands.registerCommand('rml-dev-server.downloadServer', async () => {
                try {
                    await downloadBurpServer(this.context, this.outputChannel);
                    if (this.serverProcess.isRunning()) {
                        const restartChoice = await vscode.window.showInformationMessage(
                            'BURP Dev Server downloaded. Restart server now?',
                            'Restart',
                            'Later'
                        );
                        if (restartChoice === 'Restart') {
                            await this.restart();
                        }
                    } else {
                        const startChoice = await vscode.window.showInformationMessage(
                            'BURP Dev Server downloaded. Start server now?',
                            'Start Server',
                            'Later'
                        );
                        if (startChoice === 'Start Server') {
                            await this.startOrConnect();
                        }
                    }
                } catch (err: any) {
                    vscode.window.showErrorMessage(`Failed to download BURP Server: ${err.message}`);
                }
            })
        );
    }

    public dispose(): void {
        for (const d of this.disposables) {
            d.dispose();
        }
    }
}
