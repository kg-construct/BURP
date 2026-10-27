import * as vscode from 'vscode';
import {RmlDevClient} from './client';
import {registerCommands} from './commands';
import {getConfiguration} from './config';
import {isRmlDocument} from './documentUtils';
import {RmlEvalInlayHintsProvider} from './evalInlayHintsProvider';
import {registerLanguageFeatures} from './languageFeatures';
import {ServerController} from './serverController';
import {ServerProcess} from './serverProcess';
import {StatusBarManager} from './statusBar';
import {EvaluatorViewProvider} from './ui/evaluatorView';
import {ExecutionManager} from './ui/executionManager';
import {NodeHighlightManager} from './ui/nodeHighlightManager';
import {RdfVisualizerManager} from './ui/rdfVisualizerManager';
import {SourceDataManager} from './ui/sourceDataManager';
import {StructureViewProvider} from './ui/structureView';
import {TraceProvider} from './ui/traceProvider';
import {ValidationManager} from './validationManager';
import {TypedDataContext} from './dataContext';
import {telemetry} from './telemetry';

let serverController: ServerController | null = null;
let nodeHighlightManager: NodeHighlightManager | null = null;
let traceEditorManager: TraceProvider | null = null;
let executionManager: ExecutionManager | null = null;
let rdfVisualizerManager: RdfVisualizerManager | null = null;

export async function activate(context: vscode.ExtensionContext) {
    telemetry.initialize(context);
    telemetry.capture('extension_activated');
    const outputChannel = vscode.window.createOutputChannel('RML Dev Server');
    const serverProcess = new ServerProcess(outputChannel, context);
    const client = new RmlDevClient(outputChannel);
    const statusBar = new StatusBarManager();
    nodeHighlightManager = new NodeHighlightManager(client);

    const inlayHintsProvider = new RmlEvalInlayHintsProvider(client);
    const structureViewProvider = new StructureViewProvider(client, context.extensionUri);
    const evaluatorViewProvider = new EvaluatorViewProvider(
        client,
        context.extensionUri,
        (ctx) => {
            inlayHintsProvider.setDataContext(ctx);
        },
        () => structureViewProvider.getSelectedNodeType()
    );
    const sourceDataManager = new SourceDataManager(client, context.extensionUri);
    executionManager = new ExecutionManager(client, context.extensionUri);
    rdfVisualizerManager = new RdfVisualizerManager(context.extensionUri);
    traceEditorManager = new TraceProvider(client, context.extensionUri);
    const validationManager = new ValidationManager(client);

    serverController = new ServerController(context, serverProcess, client, statusBar, outputChannel, {
        onConnected: async () => {
            const editor = vscode.window.activeTextEditor;
            if (editor && isRmlDocument(editor.document)) {
                await client.didOpenTextDocument(editor.document);
                const pos = editor.selection.active;
                structureViewProvider.updateCursor(editor.document, pos);
                evaluatorViewProvider.updateCursor(editor.document, pos);
                nodeHighlightManager?.update(editor);
            }
            inlayHintsProvider.refresh();
        }
    });

    context.subscriptions.push(
        outputChannel,
        serverProcess,
        client,
        statusBar,
        nodeHighlightManager,
        validationManager,
        serverController,
        {dispose: () => traceEditorManager?.dispose()},
        {dispose: () => executionManager?.dispose()},
        {dispose: () => rdfVisualizerManager?.dispose()}
    );

    context.subscriptions.push(
        vscode.window.registerWebviewViewProvider(StructureViewProvider.viewType, structureViewProvider),
        vscode.window.registerWebviewViewProvider(EvaluatorViewProvider.viewType, evaluatorViewProvider)
    );

    context.subscriptions.push(
        ...registerLanguageFeatures(client, inlayHintsProvider),
        ...registerCommands({
            traceProvider: traceEditorManager,
            executionManager,
            rdfVisualizerManager,
            sourceDataManager
        })
    );

    // Editor & Selection Listeners
    const syncEditorState = (editor?: vscode.TextEditor) => {
        if (editor && isRmlDocument(editor.document)) {
            const pos = editor.selection.active;
            structureViewProvider.updateCursor(editor.document, pos);
            evaluatorViewProvider.updateCursor(editor.document, pos);
            nodeHighlightManager?.update(editor);
        }
    };
    context.subscriptions.push(
        vscode.window.onDidChangeTextEditorSelection((e) => syncEditorState(e.textEditor)),
        vscode.window.onDidChangeActiveTextEditor((editor) => syncEditorState(editor))
    );

    // Document Synchronization & Validation
    context.subscriptions.push(
        vscode.workspace.onDidOpenTextDocument(async (doc) => {
            if (isRmlDocument(doc)) {
                await client.didOpenTextDocument(doc);
                inlayHintsProvider.refresh();
                validationManager.scheduleValidation(doc);
            }
        }),
        vscode.workspace.onDidChangeTextDocument(async (e) => {
            if (isRmlDocument(e.document)) {
                await client.didChangeTextDocument(e);
                inlayHintsProvider.refresh();
                validationManager.scheduleValidation(e.document);
            }
        }),
        vscode.workspace.onDidSaveTextDocument(async (doc) => {
            if (isRmlDocument(doc)) {
                await client.didSaveTextDocument(doc);
                validationManager.runValidation(doc);
            }
        }),
        vscode.workspace.onDidCloseTextDocument(async (doc) => {
            if (isRmlDocument(doc)) {
                validationManager.clear(doc.uri);
                await client.didCloseTextDocument(doc);
            }
        })
    );

    // Navigation commands
    context.subscriptions.push(
        vscode.commands.registerCommand('rml.nextInlineIteration', async () => {
            const editor = vscode.window.activeTextEditor;
            if (!editor || !client.isConnected()) {
                return;
            }
            try {
                const res = await client.stepInlineIteration({
                    structure: {
                        mappingFileUri: editor.document.uri.toString(),
                        position: {line: editor.selection.active.line, character: editor.selection.active.character}
                    } as any,
                    delta: 1
                });
                const newContext: TypedDataContext = {
                    type: 'range',
                    startIndex: res.activeIterationIndex,
                    endIndex: res.activeIterationIndex
                };
                inlayHintsProvider.setDataContext(newContext);
                evaluatorViewProvider.setDataContext(newContext);
            } catch (err) {
                console.error('Failed to step next inline iteration:', err);
            }
        }),
        vscode.commands.registerCommand('rml.previousInlineIteration', async () => {
            const editor = vscode.window.activeTextEditor;
            if (!editor || !client.isConnected()) {
                return;
            }
            try {
                const res = await client.stepInlineIteration({
                    structure: {
                        mappingFileUri: editor.document.uri.toString(),
                        position: {line: editor.selection.active.line, character: editor.selection.active.character}
                    } as any,
                    delta: -1
                });
                const newContext: TypedDataContext = {
                    type: 'range',
                    startIndex: res.activeIterationIndex,
                    endIndex: res.activeIterationIndex
                };
                inlayHintsProvider.setDataContext(newContext);
                evaluatorViewProvider.setDataContext(newContext);
            } catch (err) {
                console.error('Failed to step previous inline iteration:', err);
            }
        })
    );

    // Configuration updates
    context.subscriptions.push(
        vscode.workspace.onDidChangeConfiguration(async (e) => {
            if (e.affectsConfiguration('rml.nodeBackgroundAll')) {
                nodeHighlightManager?.update(vscode.window.activeTextEditor);
            }
            if (e.affectsConfiguration('rml.baseIRI') || e.affectsConfiguration('rml.iterationSampleLimit')
                || e.affectsConfiguration('rml.inlineHints.enableEvaluation')
                || e.affectsConfiguration('rml.inlineHints.evaluationMode')
                || e.affectsConfiguration('rml.inlineHints.joinIndexLimit')
                || e.affectsConfiguration('rml')) {
                const config = getConfiguration();
                if (client.isConnected()) {
                    await client.didChangeConfiguration({
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
                }
            }
            if (e.affectsConfiguration('rml.iterationSampleLimit')) {
                const editor = vscode.window.activeTextEditor;
                if (editor) {
                    validationManager.runValidation(editor.document);
                }
            }
            if (e.affectsConfiguration('rml.inlineHints.enableEvaluation')
                || e.affectsConfiguration('rml.inlineHints.evaluationMode')
                || e.affectsConfiguration('rml.inlineHints.joinIndexLimit')) {
                inlayHintsProvider.refresh();
            }
        })
    );

    if (getConfiguration().autoStart) {
        serverController.startOrConnect();
    }
}

export async function deactivate() {
    nodeHighlightManager?.dispose();
    traceEditorManager?.dispose();
    executionManager?.dispose();
    rdfVisualizerManager?.dispose();
    await serverController?.stop();
}
