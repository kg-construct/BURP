import * as cp from 'child_process';
import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import {getConfiguration} from './config';

export interface BurpTarget {
    type: 'jar' | 'binary';
    path: string;
    source: 'config' | 'workspace' | 'local' | 'storage' | 'path';
}

export function isCommandOnPath(command: string): boolean {
    try {
        const checkCmd = process.platform === 'win32' ? `where ${command}` : `which ${command}`;
        cp.execSync(checkCmd, {stdio: 'ignore'});
        return true;
    } catch {
        return false;
    }
}

export function getExpectedStorageJarPath(context: vscode.ExtensionContext): string {
    const version = context.extension?.packageJSON?.version || 'latest';
    return path.join(context.globalStorageUri.fsPath, 'server', `burp-${version}.jar`);
}

export function findBurpExecutable(context: vscode.ExtensionContext): BurpTarget | null {
    const config = getConfiguration();

    // 1. Explicit user configuration 'rml.burp.path'
    if (config.burpPath && config.burpPath.trim().length > 0) {
        const trimmed = config.burpPath.trim();
        if (trimmed.endsWith('.jar') && fs.existsSync(trimmed)) {
            return {type: 'jar', path: trimmed, source: 'config'};
        }
        if (fs.existsSync(trimmed) || isCommandOnPath(trimmed)) {
            return {type: 'binary', path: trimmed, source: 'config'};
        }
    }

    // 2. Workspace auto-discovery: target/burp.jar or target/burp-*.jar
    const workspaceFolders = vscode.workspace.workspaceFolders || [];
    const candidatePaths: string[] = [];
    for (const folder of workspaceFolders) {
        const root = folder.uri.fsPath;
        candidatePaths.push(
            path.join(root, 'target', 'burp.jar'),
            path.join(root, '..', 'target', 'burp.jar'),
            path.join(root, 'burp.jar')
        );
        // check for target/burp-*.jar
        const targetDir = path.join(root, 'target');
        if (fs.existsSync(targetDir)) {
            try {
                const files = fs.readdirSync(targetDir);
                for (const file of files) {
                    if (file.startsWith('burp-') && file.endsWith('.jar') && !file.includes('sources') && !file.includes('javadoc')) {
                        candidatePaths.push(path.join(targetDir, file));
                    }
                }
            } catch {
                // ignore readdir error
            }
        }
    }

    for (const candidate of candidatePaths) {
        if (fs.existsSync(candidate)) {
            return {type: 'jar', path: candidate, source: 'workspace'};
        }
    }

    // 3. Local extension folder: server/burp.jar (excluded from marketplace package via .vscodeignore)
    const localJar = path.join(__dirname, '..', 'server', 'burp.jar');
    if (fs.existsSync(localJar)) {
        return {type: 'jar', path: localJar, source: 'local'};
    }

    // 4. Downloaded JAR in global storage (matching extension version, or generic fallback)
    const versionedStorageJar = getExpectedStorageJarPath(context);
    if (fs.existsSync(versionedStorageJar)) {
        return {type: 'jar', path: versionedStorageJar, source: 'storage'};
    }
    const genericStorageJar = path.join(context.globalStorageUri.fsPath, 'server', 'burp.jar');
    if (fs.existsSync(genericStorageJar)) {
        return {type: 'jar', path: genericStorageJar, source: 'storage'};
    }

    // 5. System 'burp' executable on PATH
    if (isCommandOnPath('burp')) {
        return {type: 'binary', path: 'burp', source: 'path'};
    }

    return null;
}
