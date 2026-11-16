import * as net from 'net';
import * as vscode from 'vscode';
import {
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
    StreamInfo
} from 'vscode-languageclient/node';
import {getConfiguration} from './config';
import {RML_DOCUMENT_SELECTOR} from './documentUtils';
import {
    EvaluateParams,
    EvaluateResult,
    ExecuteParams,
    ExecuteResult,
    PlanParams,
    PlanResult,
    QuadChunkNotification,
    ReferenceDependency,
    ReferencesDependenciesParams,
    SetDataContextParams,
    SourceDataParams,
    SourceDataResult,
    StepInlineIterationParams,
    StepInlineIterationResult,
    TraceIterationParams,
    TraceParams,
    TraceResult,
    TraceResultParams
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
    private languageClient: LanguageClient | null = null;
    private socket: net.Socket | null = null;
    private outputChannel: vscode.LogOutputChannel;
    private lastLatencyMs: number | null = null;
    private quadChunkEmitter = new vscode.EventEmitter<QuadChunkNotification>();
    public readonly onQuadChunkReceived = this.quadChunkEmitter.event;

    constructor(outputChannel: vscode.LogOutputChannel) {
        this.outputChannel = outputChannel;
    }

    public getLanguageClient(): LanguageClient | null {
        return this.languageClient;
    }

    public get diagnostics(): vscode.DiagnosticCollection | undefined {
        return this.languageClient?.diagnostics;
    }

    public isConnected(): boolean {
        return this.languageClient !== null && this.languageClient.isRunning();
    }

    public getLatency(): number | null {
        return this.lastLatencyMs;
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

        const serverOptions: ServerOptions = () => {
            return new Promise<StreamInfo>((resolve, reject) => {
                const socket = net.createConnection({ port, host: '127.0.0.1' }, () => {
                    this.socket = socket;
                    resolve({ reader: socket, writer: socket });
                });
                socket.on('error', (err) => {
                    this.outputChannel.appendLine(`[Client] Socket error: ${err.message}`);
                    reject(err);
                });
                socket.on('close', () => {
                    this.outputChannel.appendLine('[Client] Socket closed.');
                });
            });
        };

        const config = getConfiguration();
        const clientOptions: LanguageClientOptions = {
            documentSelector: RML_DOCUMENT_SELECTOR as any,
            outputChannel: this.outputChannel,
            synchronize: {
                configurationSection: ['rml', 'swls']
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
            },
            middleware: {
                // Inlay hints are handled exclusively by RmlEvalInlayHintsProvider to support dynamic evaluation contexts
                provideInlayHints: () => []
            }
        };

        this.languageClient = new LanguageClient(
            'rml',
            'RML Dev Server',
            serverOptions,
            clientOptions
        );

        this.languageClient.onNotification('rml/quadChunk', (chunk: QuadChunkNotification) => {
            this.outputChannel.appendLine(`[Client] Received quad chunk for execution ${chunk.executionId}, target ${chunk.targetId}, chunkIndex ${chunk.chunkIndex}, isLast ${chunk.isLast}`);
            this.quadChunkEmitter.fire(chunk);
        });

        const initStart = Date.now();
        await this.languageClient.start();
        this.lastLatencyMs = Date.now() - initStart;

        this.outputChannel.appendLine(`[Client] Server initialized successfully (${this.lastLatencyMs}ms).`);
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
            capabilities: this.languageClient?.initializeResult?.capabilities,
            message: `RML Dev Server is healthy on port ${port} (${this.lastLatencyMs ?? 0}ms latency).`
        };
    }

    private async sendRpcRequest<T>(method: string, params: any): Promise<T> {
        this.ensureConnected();
        this.outputChannel.appendLine(`[RPC Req] ${method}`);
        try {
            const result = await this.languageClient!.sendRequest<T>(method, params);
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

    public async plan(params: PlanParams): Promise<PlanResult> {
        return this.sendRpcRequest<PlanResult>('rml/plan', params);
    }

    public async referencesDependencies(params: ReferencesDependenciesParams): Promise<ReferenceDependency[]> {
        return this.sendRpcRequest<ReferenceDependency[]>('rml/referencesDependencies', params);
    }

    public async stepInlineIteration(params: StepInlineIterationParams): Promise<StepInlineIterationResult> {
        return this.sendRpcRequest<StepInlineIterationResult>('rml/stepInlineIteration', params);
    }

    public async setDataContext(params: SetDataContextParams): Promise<void> {
        return this.sendRpcRequest<void>('rml/setDataContext', params);
    }

    public async inlayHint(params: { textDocument: { uri: string }; range: { start: { line: number; character: number }; end: { line: number; character: number } } }): Promise<any[]> {
        return this.sendRpcRequest<any[]>('textDocument/inlayHint', params);
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
        if (this.languageClient) {
            try {
                if (this.languageClient.isRunning()) {
                    await this.languageClient.stop();
                } else {
                    await this.languageClient.dispose();
                }
            } catch (ignored) {}
            this.languageClient = null;
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
