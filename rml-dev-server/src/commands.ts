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
        vscode.commands.registerCommand('rml.openStructurePanel', async () => {
            await vscode.commands.executeCommand('workbench.view.extension.rml-explorer');
        }),
        vscode.commands.registerCommand('rml.openEvaluatorPanel', async () => {
            await vscode.commands.executeCommand('workbench.view.extension.rml-explorer');
        })
    );

    disposables.push(
        vscode.commands.registerCommand(
            'rml.traceIteration',
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
            'rml.traceQuad',
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
        vscode.commands.registerCommand('rml.executeCurrent', async (uri?: vscode.Uri) => {
            const trigger_source: 'command_palette' | 'editor_title' =
                uri instanceof vscode.Uri ? 'editor_title' : 'command_palette';
            telemetry.capture('run_mapping', { trigger_source });
            await ctx.executionManager.executeMapping(uri);
        }),
        vscode.commands.registerCommand('rml.visualizeRdf', async (uri?: vscode.Uri) => {
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
        'rml.viewSourceData',
        'rml.peekLogicalView',
        'rml.peekLogicalSource'
    ];
    for (const cmd of sourceDataCommandIds) {
        disposables.push(vscode.commands.registerCommand(cmd, handleOpenSourceData));
    }

    disposables.push(
        vscode.commands.registerCommand('rml.setStudyPseudo', async () => {
            if (!telemetry.isTelemetryEnabled()) {
                const choice = await vscode.window.showWarningMessage(
                    'Telemetry is currently disabled. Enable telemetry to assign a study pseudo.',
                    'Enable Telemetry',
                    'Cancel'
                );
                if (choice !== 'Enable Telemetry') {
                    return;
                }
                await vscode.workspace
                    .getConfiguration('rml')
                    .update('telemetry.enabled', 'enabled', vscode.ConfigurationTarget.Global);
                if (!telemetry.isTelemetryEnabled()) {
                    vscode.window.showWarningMessage(
                        'Telemetry is still disabled (for example, by VS Code global settings or test mode).'
                    );
                    return;
                }
            }

            const pseudo = await vscode.window.showInputBox({
                title: 'Set Study Pseudo',
                prompt: 'Enter your assigned study pseudonym for telemetry identification',
                validateInput: (value) => (value.trim().length === 0 ? 'Study pseudo cannot be empty.' : undefined)
            });

            if (!pseudo) {
                return;
            }

            telemetry.setStudyPseudo(pseudo);
            vscode.window.showInformationMessage(`Study pseudo "${pseudo.trim()}" registered for telemetry.`);
        })
    );

    return disposables;
}
