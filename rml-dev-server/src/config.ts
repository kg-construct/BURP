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
    graphBackgroundMethod: string;
    nodeBackground: boolean;
    nodeBackgroundAll: boolean;
    telemetryEnabled: 'unspecified' | 'enabled' | 'disabled';
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
        graphBackgroundMethod: config.get<string>('graph.backgroundMethod', 'smoothHull'),
        nodeBackground: config.get<boolean>('nodeBackground', false),
        nodeBackgroundAll: config.get<boolean>('nodeBackgroundAll', false),
        telemetryEnabled: config.get<'unspecified' | 'enabled' | 'disabled'>('telemetry.enabled', 'unspecified')
    };
}
