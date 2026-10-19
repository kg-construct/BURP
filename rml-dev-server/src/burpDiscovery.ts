import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import {getConfiguration} from './config';

export interface BurpTarget {
    type: 'jar';
    path: string;
    source: 'config' | 'local';
}

export function findBurpExecutable(context: vscode.ExtensionContext): BurpTarget | null {
    const config = getConfiguration();

    // Explicit user configuration 'rml.burp.path'
    if (config.burpPath && config.burpPath.trim().length > 0) {
        const trimmed = config.burpPath.trim();
        if (trimmed.endsWith('.jar') && fs.existsSync(trimmed)) {
            return {type: 'jar', path: trimmed, source: 'config'};
        }
    }

    // Integrated/bundled with extension in server/burp.jar
    const localJar = context.asAbsolutePath
        ? context.asAbsolutePath(path.join('server', 'burp.jar'))
        : path.join(__dirname, '..', 'server', 'burp.jar');
    if (fs.existsSync(localJar)) {
        return {type: 'jar', path: localJar, source: 'local'};
    }

    return null;
}
