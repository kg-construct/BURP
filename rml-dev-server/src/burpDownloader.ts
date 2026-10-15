import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import {downloadFile} from './downloader';

export async function downloadBurpServer(
    context: vscode.ExtensionContext,
    outputChannel: vscode.OutputChannel,
    force: boolean = false
): Promise<string> {
    const version = context.extension?.packageJSON?.version || 'latest';
    const serverDir = path.join(context.globalStorageUri.fsPath, 'server');
    if (!fs.existsSync(serverDir)) {
        fs.mkdirSync(serverDir, {recursive: true});
    }

    const targetJarPath = path.join(serverDir, `burp-${version}.jar`);

    // If active version already exists and re-download was not forced
    if (fs.existsSync(targetJarPath) && !force) {
        const choice = await vscode.window.showInformationMessage(
            `BURP Dev Server v${version} is already installed. Re-download and overwrite?`,
            'Re-download',
            'Cancel'
        );
        if (choice !== 'Re-download') {
            outputChannel.appendLine(`[BURP Downloader] Re-download skipped. Using existing JAR: ${targetJarPath}`);
            return targetJarPath;
        }
    }

    const primaryUrl = `https://github.com/kg-construct/BURP/releases/download/v${version}/burp.jar`;
    const fallbackUrl = 'https://github.com/kg-construct/BURP/releases/latest/download/burp.jar';

    outputChannel.appendLine(`[BURP Downloader] Downloading BURP Server from ${primaryUrl}...`);

    try {
        await downloadFile(primaryUrl, targetJarPath, {
            title: `Downloading BURP Dev Server (${version})...`,
            outputChannel
        });
    } catch (err: any) {
        if (primaryUrl !== fallbackUrl) {
            outputChannel.appendLine(`[BURP Downloader] Tagged download failed (${err.message}). Trying latest release fallback...`);
            await downloadFile(fallbackUrl, targetJarPath, {
                title: `Downloading BURP Dev Server (latest release)...`,
                outputChannel
            });
        } else {
            throw err;
        }
    }

    // Clean up older downloaded JARs to reclaim disk space (~50MB per version)
    cleanupOldBurpJars(serverDir, path.basename(targetJarPath), outputChannel);

    outputChannel.appendLine(`[BURP Downloader] BURP Dev Server successfully installed at: ${targetJarPath}`);

    // If user has a conflicting manual path configured, inform them
    const config = vscode.workspace.getConfiguration('rml');
    const customBurpPath = config.get<string>('burp.path');
    if (customBurpPath && customBurpPath.trim().length > 0) {
        vscode.window.showInformationMessage(
            `BURP v${version} downloaded, but 'rml.burp.path' is currently configured to: ${customBurpPath}`,
            'Clear Setting'
        ).then((action) => {
            if (action === 'Clear Setting') {
                config.update('burp.path', '', vscode.ConfigurationTarget.Global);
            }
        });
    }

    return targetJarPath;
}

function cleanupOldBurpJars(serverDir: string, currentJarName: string, outputChannel: vscode.OutputChannel): void {
    try {
        const files = fs.readdirSync(serverDir);
        for (const file of files) {
            if (file.startsWith('burp-') && file.endsWith('.jar') && file !== currentJarName) {
                const oldPath = path.join(serverDir, file);
                try {
                    fs.unlinkSync(oldPath);
                    outputChannel.appendLine(`[BURP Downloader] Removed older JAR version: ${file}`);
                } catch {
                    // ignore individual unlink error
                }
            }
        }
    } catch {
        // ignore readdir error
    }
}
