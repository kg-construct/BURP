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
    const outputChannel = vscode.window.createOutputChannel('RML Dev Server', {log: true});
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
            const activeUri = vscode.window.activeTextEditor?.document.uri.toString();
            inlayHintsProvider.setDataContext(ctx, activeUri);
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

    // Document Synchronization (refresh local inlay hints on open/change)
    context.subscriptions.push(
        vscode.workspace.onDidOpenTextDocument((doc) => {
            if (isRmlDocument(doc)) {
                inlayHintsProvider.refresh();
            }
        }),
        vscode.workspace.onDidChangeTextDocument((e) => {
            if (isRmlDocument(e.document)) {
                inlayHintsProvider.refresh();
            }
        }),
        vscode.workspace.onDidCloseTextDocument((doc) => {
            if (isRmlDocument(doc)) {
                validationManager.clear(doc.uri);
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
                inlayHintsProvider.setDataContext(newContext, editor.document.uri.toString());
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
                inlayHintsProvider.setDataContext(newContext, editor.document.uri.toString());
                evaluatorViewProvider.setDataContext(newContext);
            } catch (err) {
                console.error('Failed to step previous inline iteration:', err);
            }
        })
    );

    // Configuration updates (LanguageClient automatically synchronizes 'rml' and 'swls' configuration sections)
    context.subscriptions.push(
        vscode.workspace.onDidChangeConfiguration((e) => {
            if (e.affectsConfiguration('rml.nodeBackgroundAll')) {
                nodeHighlightManager?.update(vscode.window.activeTextEditor);
            }
            if (e.affectsConfiguration('rml.iterationSampleLimit')) {
                const editor = vscode.window.activeTextEditor;
                if (editor && isRmlDocument(editor.document)) {
                    void editor.document.save();
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
