import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import {getConfiguration} from './config';

export interface BurpTarget {
    type: 'jar';
    path: string;
    source: 'config' | 'storage' | 'local';
}

export function findBurpExecutable(context: vscode.ExtensionContext): BurpTarget | null {
    const config = getConfiguration();

    // 1. Explicit user configuration 'rml.burp.path'
    if (config.burpPath && config.burpPath.trim().length > 0) {
        const trimmed = config.burpPath.trim();
        if (trimmed.endsWith('.jar') && fs.existsSync(trimmed)) {
            return {type: 'jar', path: trimmed, source: 'config'};
        }
    }

    // 2. Downloaded JAR in global storage (matching serverVersion)
    const version = context.extension?.packageJSON?.serverVersion;
    const versionedStorageJar = version
        ? path.join(context.globalStorageUri.fsPath, 'server', `burp-${version}.jar`)
        : null;
    if (versionedStorageJar && fs.existsSync(versionedStorageJar)) {
        return {type: 'jar', path: versionedStorageJar, source: 'storage'};
    }

    // 3. Integrated/bundled with extension in server/burp.jar
    const localJar = context.asAbsolutePath
        ? context.asAbsolutePath(path.join('server', 'burp.jar'))
        : path.join(__dirname, '..', 'server', 'burp.jar');
    if (fs.existsSync(localJar)) {
        return {type: 'jar', path: localJar, source: 'local'};
    }

    return null;
}
