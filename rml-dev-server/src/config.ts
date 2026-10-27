import * as vscode from 'vscode';

export interface RmlConfiguration {
    burpPath: string;
    javaPath: string;
    serverPort: number;
    spawnProcess: boolean;
    extraArgs: string[];
    autoStart: boolean;
    baseIRI: string;
    inlineHintsEnableEvaluation: boolean;
    inlineHintsEvaluationMode: 'trace' | 'independentEvaluation';
    inlineHintsJoinIndexLimit: number | null;
    graphBackgroundMethod: string;
    nodeBackground: boolean;
    nodeBackgroundAll: boolean;
    telemetryEnabled: 'unspecified' | 'enabled' | 'disabled';
    iterationSampleLimit: number | null;
}

export function getConfiguration(): RmlConfiguration {
    const config = vscode.workspace.getConfiguration('rml');
    return {
        burpPath: config.get<string>('burp.path', ''),
        javaPath: config.get<string>('java.path', 'java'),
        serverPort: config.get<number>('server.port', 0),
        spawnProcess: config.get<boolean>('server.spawnProcess', true),
        extraArgs: config.get<string[]>('server.extraArgs', []),
        autoStart: config.get<boolean>('server.autoStart', true),
        baseIRI: config.get<string>('baseIRI', 'http://example.org/'),
        inlineHintsEnableEvaluation: config.get<boolean>('inlineHints.enableEvaluation', true),
        inlineHintsEvaluationMode: config.get<'trace' | 'independentEvaluation'>('inlineHints.evaluationMode', 'trace'),
        inlineHintsJoinIndexLimit: config.get<number | null>('inlineHints.joinIndexLimit', 1000),
        graphBackgroundMethod: config.get<string>('graph.backgroundMethod', 'smoothHull'),
        nodeBackground: config.get<boolean>('nodeBackground', false),
        nodeBackgroundAll: config.get<boolean>('nodeBackgroundAll', false),
        telemetryEnabled: config.get<'unspecified' | 'enabled' | 'disabled'>('telemetry.enabled', 'unspecified'),
        iterationSampleLimit: config.get<number | null>('iterationSampleLimit', 20)
    };
}
