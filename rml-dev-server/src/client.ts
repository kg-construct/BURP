import * as net from 'net';
import * as vscode from 'vscode';
import * as rpc from 'vscode-jsonrpc/node';
import {getConfiguration} from './config';
import {
    EvaluateParams,
    EvaluateResult,
    ExecuteParams,
    ExecuteResult,
    InlineEvaluateItem,
    InlineEvaluateParams,
    PlanParams,
    PlanResult,
    QuadChunkNotification,
    ReferenceDependency,
    ReferencesDependenciesParams,
    RmlDiagnostic,
    SourceDataParams,
    SourceDataResult,
    StepInlineIterationParams,
    StepInlineIterationResult,
    TraceIterationParams,
    TraceParams,
    TraceResult,
    TraceResultParams,
    ValidateParams
} from './generated/types';

export interface HealthCheckResult {
    ok: boolean;
    serverPort?: number;
    latencyMs?: number;
    capabilities?: any;
    message: string;
    error?: any;
}

export class RmlDevClient {
    private connection: rpc.MessageConnection | null = null;
    private socket: net.Socket | null = null;
    private outputChannel: vscode.OutputChannel;
    private isInitialized = false;
    private serverCapabilities: any = null;
    private lastLatencyMs: number | null = null;
    private quadChunkEmitter = new vscode.EventEmitter<QuadChunkNotification>();
    public readonly onQuadChunkReceived = this.quadChunkEmitter.event;

    constructor(outputChannel: vscode.OutputChannel) {
        this.outputChannel = outputChannel;
    }

    public isConnected(): boolean {
        return this.connection !== null && this.isInitialized;
    }

    public onQuadChunk(executionId: string, callback: (chunk: QuadChunkNotification) => void): vscode.Disposable {
        return this.onQuadChunkReceived((notification) => {
            if (notification.executionId === executionId) {
                callback(notification);
            }
        });
    }

    public async connect(port: number): Promise<void> {
        await this.disconnect();

        this.outputChannel.appendLine(`[Client] Connecting to BURP Dev Server on port ${port}...`);

        return new Promise<void>((resolve, reject) => {
            const socket = net.createConnection({ port, host: '127.0.0.1' }, async () => {
                this.socket = socket;
                this.connection = rpc.createMessageConnection(
                    new rpc.SocketMessageReader(socket),
                    new rpc.SocketMessageWriter(socket)
                );

                this.connection.onNotification('rml/quadChunk', (chunk: QuadChunkNotification) => {
                    this.outputChannel.appendLine(`[Client] Received quad chunk for execution ${chunk.executionId}, target ${chunk.targetId}, chunkIndex ${chunk.chunkIndex}, isLast ${chunk.isLast}`);
                    this.quadChunkEmitter.fire(chunk);
                });

                this.connection.onRequest('workspace/configuration', (params: { items: Array<{ scopeUri?: string; section?: string }> }) => {
                    this.outputChannel.appendLine(`[Client] Received workspace/configuration request for ${params.items.length} items`);
                    return params.items.map((item) => {
                        const scope = item.scopeUri ? vscode.Uri.parse(item.scopeUri) : undefined;
                        if (!item.section) {
                            return null;
                        }
                        if (item.section === 'rml') {
                            const config = vscode.workspace.getConfiguration('rml', scope);
                            return {
                                baseIRI: config.get<string>('baseIRI', 'http://example.org/'),
                                burp: { path: config.get<string>('burp.path', '') },
                                java: { path: config.get<string>('java.path', 'java') },
                                server: {
                                    port: config.get<number>('server.port', 0),
                                    spawnProcess: config.get<boolean>('server.spawnProcess', true),
                                    extraArgs: config.get<string[]>('server.extraArgs', []),
                                    autoStart: config.get<boolean>('server.autoStart', true)
                                },
                                inlineHints: {
                                    enableEvaluation: config.get<boolean>('inlineHints.enableEvaluation', true),
                                    evaluationMode: config.get<string>('inlineHints.evaluationMode', 'trace'),
                                    joinIndexLimit: config.get<number>('inlineHints.joinIndexLimit', 1000)
                                },
                                iterationSampleLimit: config.get<number | null>('iterationSampleLimit', 20),
                                graph: {
                                    backgroundMethod: config.get<string>('graph.backgroundMethod', 'smoothHull')
                                }
                            };
                        }
                        if (item.section === 'rml.baseIRI') {
                            return vscode.workspace.getConfiguration('rml', scope).get<string>('baseIRI', 'http://example.org/');
                        }
                        if (item.section === 'rml.iterationSampleLimit') {
                            return vscode.workspace.getConfiguration('rml', scope).get<number | null>('iterationSampleLimit', 20);
                        }
                        return vscode.workspace.getConfiguration(item.section, scope);
                    });
                });

                this.connection.listen();
                this.outputChannel.appendLine('[Client] JSON-RPC connection established. Initializing...');

                try {
                    const initStart = Date.now();
                    const config = getConfiguration();
                    const initResult = await this.connection.sendRequest('initialize', {
                        processId: process.pid,
                        rootUri: vscode.workspace.workspaceFolders?.[0]?.uri.toString() || null,
                        capabilities: {
                            workspace: {
                                configuration: true,
                                didChangeConfiguration: {
                                    dynamicRegistration: true
                                }
                            }
                        },
                        initializationOptions: {
                            rml: {
                                baseIRI: config.baseIRI,
                                inlineHints: {
                                    enableEvaluation: config.inlineHintsEnableEvaluation,
                                    evaluationMode: config.inlineHintsEvaluationMode,
                                    joinIndexLimit: config.inlineHintsJoinIndexLimit
                                },
                                iterationSampleLimit: config.iterationSampleLimit
                            }
                        }
                    });
                    this.lastLatencyMs = Date.now() - initStart;

                    this.serverCapabilities = (initResult as any)?.capabilities;
                    this.connection.sendNotification('initialized', {});
                    this.connection.sendNotification('workspace/didChangeConfiguration', {
                        settings: {
                            rml: {
                                baseIRI: config.baseIRI,
                                inlineHints: {
                                    enableEvaluation: config.inlineHintsEnableEvaluation,
                                    evaluationMode: config.inlineHintsEvaluationMode,
                                    joinIndexLimit: config.inlineHintsJoinIndexLimit
                                },
                                iterationSampleLimit: config.iterationSampleLimit
                            }
                        }
                    });
                    this.isInitialized = true;
                    this.outputChannel.appendLine(`[Client] Server initialized successfully (${this.lastLatencyMs}ms).`);
                    resolve();
                } catch (err: any) {
                    this.outputChannel.appendLine(`[Client] Initialization error: ${err.message}`);
                    reject(err);
                }
            });

            socket.on('error', (err) => {
                this.outputChannel.appendLine(`[Client] Socket error: ${err.message}`);
                reject(err);
            });

            socket.on('close', () => {
                this.outputChannel.appendLine('[Client] Socket closed.');
                this.disconnect();
            });
        });
    }

    public async healthCheck(port?: number): Promise<HealthCheckResult> {
        if (!this.isConnected()) {
            return {
                ok: false,
                serverPort: port,
                message: 'RML Dev Server is not connected.'
            };
        }

        return {
            ok: true,
            serverPort: port,
            latencyMs: this.lastLatencyMs ?? 0,
            capabilities: this.serverCapabilities,
            message: `RML Dev Server is healthy on port ${port} (${this.lastLatencyMs ?? 0}ms latency).`
        };
    }

    public async didOpenTextDocument(document: vscode.TextDocument): Promise<void> {
        if (!this.isConnected()) {
            return;
        }
        this.connection?.sendNotification('textDocument/didOpen', {
            textDocument: {
                uri: document.uri.toString(),
                languageId: document.languageId,
                version: document.version,
                text: document.getText()
            }
        });
    }

    public async didChangeTextDocument(event: vscode.TextDocumentChangeEvent): Promise<void> {
        if (!this.isConnected()) {
            return;
        }
        this.connection?.sendNotification('textDocument/didChange', {
            textDocument: {
                uri: event.document.uri.toString(),
                version: event.document.version
            },
            contentChanges: [
                {
                    text: event.document.getText()
                }
            ]
        });
    }

    public async didCloseTextDocument(document: vscode.TextDocument): Promise<void> {
        if (!this.isConnected()) {
            return;
        }
        this.connection?.sendNotification('textDocument/didClose', {
            textDocument: {
                uri: document.uri.toString()
            }
        });
    }

    public async didSaveTextDocument(document: vscode.TextDocument): Promise<void> {
        if (!this.isConnected()) {
            return;
        }
        this.connection?.sendNotification('textDocument/didSave', {
            textDocument: {
                uri: document.uri.toString()
            }
        });
    }

    public async didChangeConfiguration(params: { settings: any }): Promise<void> {
        if (!this.isConnected()) {
            return;
        }
        this.connection?.sendNotification('workspace/didChangeConfiguration', params);
    }

    private async sendRpcRequest<T>(method: string, params: any): Promise<T> {
        this.ensureConnected();
        this.outputChannel.appendLine(`[RPC Req] ${method}`);
        try {
            const result = await this.connection!.sendRequest<T>(method, params);
            this.outputChannel.appendLine(`[RPC Res] ${method} -> ${JSON.stringify(result)}`);
            return result;
        } catch (err: any) {
            this.outputChannel.appendLine(`[RPC Err] ${method} -> ${err.message || JSON.stringify(err)}`);
            throw err;
        }
    }

    // Custom RML Protocol methods
    public async evaluate(params: EvaluateParams): Promise<EvaluateResult[]> {
        return this.sendRpcRequest<EvaluateResult[]>('rml/evaluate', params);
    }

    public async trace(params: TraceParams): Promise<TraceResult> {
        return this.sendRpcRequest<TraceResult>('rml/trace', params);
    }

    public async traceIteration(params: TraceIterationParams): Promise<TraceResult> {
        return this.sendRpcRequest<TraceResult>('rml/traceIteration', params);
    }

    public async traceResult(params: TraceResultParams): Promise<TraceResult> {
        return this.sendRpcRequest<TraceResult>('rml/traceResult', params);
    }

    public async completion(params: { textDocument: { uri: string }; position: { line: number; character: number } }): Promise<any> {
        return this.sendRpcRequest('textDocument/completion', params);
    }

    public async plan(params: PlanParams): Promise<PlanResult> {
        return this.sendRpcRequest<PlanResult>('rml/plan', params);
    }


    public async referencesDependencies(params: ReferencesDependenciesParams): Promise<ReferenceDependency[]> {
        return this.sendRpcRequest<ReferenceDependency[]>('rml/referencesDependencies', params);
    }

    public async inlineEvaluate(params: InlineEvaluateParams): Promise<InlineEvaluateItem[]> {
        return this.sendRpcRequest<InlineEvaluateItem[]>('rml/inlineEvaluate', params);
    }

    public async stepInlineIteration(params: StepInlineIterationParams): Promise<StepInlineIterationResult> {
        return this.sendRpcRequest<StepInlineIterationResult>('rml/stepInlineIteration', params);
    }

    public async inlayHint(params: { textDocument: { uri: string }; range: { start: { line: number; character: number }; end: { line: number; character: number } } }): Promise<any[]> {
        return this.sendRpcRequest<any[]>('textDocument/inlayHint', params);
    }

    public async codeLens(params: { textDocument: { uri: string } }): Promise<any[]> {
        return this.sendRpcRequest<any[]>('textDocument/codeLens', params);
    }

    public async validate(params: ValidateParams): Promise<RmlDiagnostic[]> {
        return this.sendRpcRequest<RmlDiagnostic[]>('rml/validate', params);
    }

    public async getSourceData(params: SourceDataParams): Promise<SourceDataResult> {
        return this.sendRpcRequest<SourceDataResult>('rml/source/data', params);
    }

    private ensureConnected(): void {
        if (!this.isConnected()) {
            throw new Error('RML Dev Server is not connected.');
        }
    }

    public async disconnect(): Promise<void> {
        this.isInitialized = false;
        this.serverCapabilities = null;
        if (this.connection) {
            try {
                this.connection.dispose();
            } catch (ignored) {}
            this.connection = null;
        }
        if (this.socket) {
            try {
                this.socket.destroy();
            } catch (ignored) {}
            this.socket = null;
        }
    }

    public async execute(params: ExecuteParams): Promise<ExecuteResult> {
        return this.sendRpcRequest<ExecuteResult>('rml/execute', params);
    }

    public async clearCache(): Promise<void> {
        return this.sendRpcRequest<void>('rml/clearCache', {});
    }

    public dispose(): void {
        this.disconnect();
        this.quadChunkEmitter.dispose();
    }
}
