import * as cp from 'child_process';
import * as vscode from 'vscode';
import {BurpTarget, findBurpExecutable} from './burpDiscovery';
import {getConfiguration} from './config';

export enum JavaErrorCode {
    MISSING = 'MISSING',
    TOO_OLD = 'TOO_OLD'
}

export class JavaError extends Error {
    constructor(public code: JavaErrorCode, public version?: number) {
        super(code === JavaErrorCode.MISSING ? 'Java executable is missing' : `Java version is too old: ${version}`);
        this.name = 'JavaError';
    }
}

export class ServerProcess {
    private process: cp.ChildProcess | null = null;
    private port: number | null = null;
    private outputChannel: vscode.OutputChannel;
    private onExitEmitter = new vscode.EventEmitter<number | null>();
    public readonly onExit = this.onExitEmitter.event;

    constructor(outputChannel: vscode.OutputChannel, private readonly context?: vscode.ExtensionContext) {
        this.outputChannel = outputChannel;
    }

    public isRunning(): boolean {
        return this.process !== null && !this.process.killed;
    }

    public getPort(): number | null {
        return this.port;
    }

    public async start(): Promise<number> {
        const config = getConfiguration();

        if (!config.spawnProcess) {
            if (config.serverPort > 0) {
                this.port = config.serverPort;
                this.outputChannel.appendLine(`[ServerProcess] Configured for external server mode (spawnProcess=false). Connecting to port ${this.port}`);
                return this.port;
            } else {
                throw new Error("Setting 'rml.server.spawnProcess' is false, but 'rml.server.port' is not configured (> 0). Please specify the external port.");
            }
        }

        if (this.isRunning() && this.port !== null) {
            return this.port;
        }

        await this.stop();

        const {command, args, target} = this.resolveBurpExecution();

        // 1. Verify Java before spawning
        if (command === config.javaPath || command === 'java' || command.endsWith('java') || command.endsWith('java.exe')) {
            await this.checkJavaVersion(command);
        }

        // 2. Check and log BURP server version
        if (target) {
            await this.checkBurpVersion(target);
        }

        this.outputChannel.appendLine(`[ServerProcess] Starting BURP Dev Server: ${command} ${args.join(' ')}`);

        return new Promise<number>((resolve, reject) => {
            let settled = false;

            try {
                this.process = cp.spawn(command, args, {
                    cwd: vscode.workspace.workspaceFolders?.[0]?.uri.fsPath || process.cwd(),
                    env: process.env
                });
            } catch (err: any) {
                this.outputChannel.appendLine(`[ServerProcess] Failed to spawn process: ${err.message}`);
                return reject(err);
            }

            this.process.on('error', (err: any) => {
                this.outputChannel.appendLine(`[ServerProcess] Process error: ${err.message}`);
                if (!settled) {
                    settled = true;
                    if (err.code === 'ENOENT') {
                        if (command === config.javaPath || command === 'java' || command.endsWith('java') || command.endsWith('java.exe')) {
                            reject(new JavaError(JavaErrorCode.MISSING));
                        } else {
                            reject(new Error(`Failed to start BURP server (${command}): executable not found.`));
                        }
                    } else {
                        reject(new Error(`Failed to start BURP server (${command}): ${err.message}.`));
                    }
                }
            });

            this.process.on('exit', (code, signal) => {
                this.outputChannel.appendLine(`[ServerProcess] Server process exited with code ${code}, signal ${signal}`);
                this.process = null;
                this.port = null;
                this.onExitEmitter.fire(code);
                if (!settled) {
                    settled = true;
                    reject(new Error(`BURP Dev Server exited prematurely with code ${code}`));
                }
            });

            this.process.stdout?.on('data', (data: Buffer) => {
                const text = data.toString();
                this.outputChannel.append(text);

                // Match "Dev Server listening on port: <port>"
                const match = text.match(/(?:Dev|dev)\s+Server\s+listening\s+on\s+port:\s*(\d+)/i);
                if (match && !settled) {
                    this.port = parseInt(match[1], 10);
                    settled = true;
                    this.outputChannel.appendLine(`[ServerProcess] Detected server listening on port ${this.port}`);
                    resolve(this.port);
                }
            });

            this.process.stderr?.on('data', (data: Buffer) => {
                const text = data.toString();
                this.outputChannel.append(`[STDERR] ${text}`);
            });

            // Timeout after 15 seconds if port not detected
            setTimeout(() => {
                if (!settled) {
                    settled = true;
                    reject(new Error('Timed out waiting for BURP Dev Server to start and announce listening port.'));
                }
            }, 15000);
        });
    }

    public async stop(): Promise<void> {
        if (this.process) {
            this.outputChannel.appendLine('[ServerProcess] Stopping BURP Dev Server...');
            const proc = this.process;
            this.process = null;
            this.port = null;

            return new Promise<void>((resolve) => {
                proc.once('exit', () => resolve());
                proc.kill('SIGTERM');
                setTimeout(() => {
                    if (!proc.killed) {
                        proc.kill('SIGKILL');
                    }
                    resolve();
                }, 2000);
            });
        }
    }

    private resolveBurpExecution(): { command: string; args: string[]; target?: BurpTarget } {
        const config = getConfiguration();
        const devArg = config.serverPort > 0 ? `-d=${config.serverPort}` : '-d';

        if (this.context) {
            const target = findBurpExecutable(this.context);
            if (target) {
                this.outputChannel.appendLine(`[ServerProcess] Using BURP (${target.source}): ${target.path}`);
                return {
                    command: config.javaPath,
                    args: ['-jar', target.path, devArg, ...config.extraArgs],
                    target
                };
            }
        }

        if (config.burpPath && config.burpPath.trim().length > 0) {
            const trimmed = config.burpPath.trim();
            if (trimmed.endsWith('.jar')) {
                return {
                    command: config.javaPath,
                    args: ['-jar', trimmed, devArg, ...config.extraArgs],
                    target: {type: 'jar', path: trimmed, source: 'config'}
                };
            }
        }

        throw new Error('BURP JAR could not be found. Please download BURP Dev Server or configure rml.burp.path.');
    }

    private async checkBurpVersion(target: BurpTarget): Promise<void> {
        const config = getConfiguration();
        const checkCmd = `"${config.javaPath}" -jar "${target.path}" --version`;

        try {
            const actualVersion = await new Promise<string>((resolve, reject) => {
                cp.exec(checkCmd, {timeout: 3000}, (error, stdout) => {
                    if (error) {
                        return reject(error);
                    }
                    resolve((stdout || '').trim());
                });
            });

            this.outputChannel.appendLine(`BURP server version: ${actualVersion}`);

            const expectedVersion = this.context?.extension?.packageJSON?.serverVersion;
            if (expectedVersion && actualVersion !== expectedVersion) {
                this.outputChannel.appendLine(
                    `WARNING: BURP server version mismatch. Running ${actualVersion}, expected ${expectedVersion}.`
                );
            }
        } catch (err: any) {
            this.outputChannel.appendLine(`[ServerProcess] Could not determine BURP server version: ${err.message}`);
        }
    }

    private async checkJavaVersion(javaCmd: string): Promise<void> {
        return new Promise((resolve, reject) => {
            cp.exec(`"${javaCmd}" -version`, (error, stdout, stderr) => {
                if (error) {
                    return reject(new JavaError(JavaErrorCode.MISSING));
                }

                // Java usually outputs its version to stderr
                const output = stderr.toString() + stdout.toString();
                const match = output.match(/version "(\d+)\.(\d+).*?"/i) || output.match(/version "(\d+)((?:\.[0-9]+)*).*?"/i);

                if (match) {
                    const major = parseInt(match[1]);
                    const actualMajor = major === 1 ? parseInt(match[2]) : major;

                    if (actualMajor < 21) {
                        return reject(new JavaError(JavaErrorCode.TOO_OLD, actualMajor));
                    }
                }
                resolve();
            });
        });
    }

    public dispose(): void {
        this.stop();
        this.onExitEmitter.dispose();
    }
}
