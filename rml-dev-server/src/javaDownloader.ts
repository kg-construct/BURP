import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';
import * as vscode from 'vscode';
import * as tar from 'tar';
import AdmZip from 'adm-zip';
import {downloadFile} from './downloader';

export async function downloadJre(context: vscode.ExtensionContext, outputChannel: vscode.OutputChannel): Promise<string> {
    const javaVersion = '25';
    const osName = getOsName();
    const arch = getArch();

    // Target directory in extension's global storage
    const storagePath = context.globalStorageUri.fsPath;
    if (!fs.existsSync(storagePath)) {
        fs.mkdirSync(storagePath, {recursive: true});
    }

    const jreDir = path.join(storagePath, `jre-${javaVersion}-${osName}-${arch}`);
    const binExt = os.platform() === 'win32' ? '.exe' : '';
    let javaExecutable = path.join(jreDir, 'bin', `java${binExt}`);

    // Check if it's already downloaded
    if (fs.existsSync(javaExecutable)) {
        return javaExecutable;
    }

    // For macOS, the extracted folder structure is slightly different (Contents/Home/bin/java)
    if (os.platform() === 'darwin') {
        javaExecutable = path.join(jreDir, 'Contents', 'Home', 'bin', `java`);
        if (fs.existsSync(javaExecutable)) {
            return javaExecutable;
        }
    }

    const apiUrl = `https://api.adoptium.net/v3/binary/latest/${javaVersion}/ga/${osName}/${arch}/jre/hotspot/normal/eclipse`;
    const ext = os.platform() === 'win32' ? '.zip' : '.tar.gz';
    const archivePath = path.join(storagePath, `jre-archive${ext}`);

    outputChannel.appendLine(`[Java Downloader] Downloading JRE 25 from ${apiUrl}...`);

    await downloadFile(apiUrl, archivePath, {
        title: "Downloading Java Runtime (JRE 25)...",
        outputChannel
    });

    // Extract using robust npm libraries
    outputChannel.appendLine(`[Java Downloader] Extracting archive...`);
    fs.mkdirSync(jreDir, {recursive: true});
    if (archivePath.endsWith('.zip')) {
        const zip = new AdmZip(archivePath);
        zip.extractAllTo(jreDir, true);
    } else {
        await tar.x({
            file: archivePath,
            cwd: jreDir
        });
    }

    if (fs.existsSync(archivePath)) {
        fs.unlinkSync(archivePath); // Cleanup
    }

    // Locate the java executable inside the extracted folder
    // Adoptium archives usually contain a root folder like 'jdk-25.0.1+15-jre'
    const extractedDirs = fs.readdirSync(jreDir).filter(f => fs.statSync(path.join(jreDir, f)).isDirectory());
    if (extractedDirs.length === 1) {
        const rootDir = path.join(jreDir, extractedDirs[0]);
        if (os.platform() === 'darwin') {
            javaExecutable = path.join(rootDir, 'Contents', 'Home', 'bin', 'java');
        } else {
            javaExecutable = path.join(rootDir, 'bin', `java${binExt}`);
        }

        // Update the configuration to point to this downloaded JRE
        const config = vscode.workspace.getConfiguration('rml');
        await config.update('java.path', javaExecutable, vscode.ConfigurationTarget.Global);
        outputChannel.appendLine(`[Java Downloader] JRE 25 successfully installed at: ${javaExecutable}`);
        return javaExecutable;
    }

    throw new Error('Failed to locate java executable in the extracted JRE archive.');
}

function getOsName() {
    switch (os.platform()) {
        case 'win32':
            return 'windows';
        case 'darwin':
            return 'mac';
        default:
            return 'linux';
    }
}

function getArch() {
    switch (os.arch()) {
        case 'x64':
            return 'x64';
        case 'arm64':
            return 'aarch64';
        default:
            throw new Error(`Unsupported architecture: ${os.arch()}`);
    }
}
