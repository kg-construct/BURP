import * as vscode from 'vscode';
import {isRmlDocument} from './documentUtils';
import {StructureSelector} from './generated/types';
import {ExecutionManager} from './ui/executionManager';
import {RdfVisualizerManager} from './ui/rdfVisualizerManager';
import {SourceDataManager} from './ui/sourceDataManager';
import {TraceProvider, TraceQuadTarget} from './ui/traceProvider';
import {telemetry} from './telemetry';

export interface CommandContext {
    traceProvider: TraceProvider;
    executionManager: ExecutionManager;
    rdfVisualizerManager: RdfVisualizerManager;
    sourceDataManager: SourceDataManager;
}

export function registerCommands(ctx: CommandContext): vscode.Disposable[] {
    const disposables: vscode.Disposable[] = [];

    disposables.push(
        vscode.commands.registerCommand('rml-dev-server.openStructurePanel', async () => {
            await vscode.commands.executeCommand('workbench.view.extension.rml-explorer');
        }),
        vscode.commands.registerCommand('rml-dev-server.openEvaluatorPanel', async () => {
            await vscode.commands.executeCommand('workbench.view.extension.rml-explorer');
        })
    );

    disposables.push(
        vscode.commands.registerCommand(
            'rml-dev-server.traceIteration',
            async (mappingUri?: vscode.Uri | string, structure?: StructureSelector, iterationIndex?: number) => {
                if (!mappingUri || !structure || iterationIndex === undefined) {
                    const editor = vscode.window.activeTextEditor;
                    if (!editor || !isRmlDocument(editor.document)) {
                        vscode.window.showWarningMessage('Please open an RML document to trace.');
                        return;
                    }
                    mappingUri = editor.document.uri;
                    structure = {
                        type: 'document',
                        textDocument: {uri: editor.document.uri.toString()}
                    } as StructureSelector;
                    iterationIndex = 0;
                }
                telemetry.capture('trace_from_source', {
                    iteration_index: iterationIndex
                });
                await ctx.traceProvider.openTraceIteration(mappingUri, structure, iterationIndex);
            }
        ),
        vscode.commands.registerCommand(
            'rml-dev-server.traceQuad',
            async (mappingUri?: vscode.Uri | string, structure?: StructureSelector, quad?: TraceQuadTarget) => {
                if (!mappingUri || !structure || !quad) {
                    vscode.window.showWarningMessage('Tracing a quad requires mapping URI, structure selector, and target quad.');
                    return;
                }
                telemetry.capture('trace_from_statement');
                await ctx.traceProvider.openTraceQuad(mappingUri, structure, quad);
            }
        )
    );

    disposables.push(
        vscode.commands.registerCommand('rml-dev-server.executeCurrent', async (uri?: vscode.Uri) => {
            const trigger_source: 'command_palette' | 'editor_title' =
                uri instanceof vscode.Uri ? 'editor_title' : 'command_palette';
            telemetry.capture('run_mapping', { trigger_source });
            await ctx.executionManager.executeMapping(uri);
        }),
        vscode.commands.registerCommand('rml-dev-server.visualizeRdf', async (uri?: vscode.Uri) => {
            telemetry.capture('visualise_rdf');
            await ctx.rdfVisualizerManager.visualizeRdf(uri);
        })
    );

    const handleOpenSourceData = async (arg?: any) => {
        if (!arg) {
            const editor = vscode.window.activeTextEditor;
            if (editor && isRmlDocument(editor.document)) {
                const pos = editor.selection.active;
                await ctx.sourceDataManager.openSourceData({
                    type: 'cursor',
                    mappingFileUri: editor.document.uri.toString(),
                    position: {line: pos.line, character: pos.character}
                } as any);
            }
            return;
        }
        await ctx.sourceDataManager.openSourceData(arg);
    };

    const sourceDataCommandIds = [
        'rml-dev-server.viewSourceData',
        'rml.viewSourceData',
        'rml-dev-server.peekLogicalView',
        'rml.peekLogicalView',
        'rml-dev-server.peekLogicalSource',
        'rml.peekLogicalSource'
    ];
    for (const cmd of sourceDataCommandIds) {
        disposables.push(vscode.commands.registerCommand(cmd, handleOpenSourceData));
    }

    return disposables;
}
