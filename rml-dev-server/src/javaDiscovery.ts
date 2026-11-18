import { findRuntimes } from 'jdk-utils';
import * as os from 'os';
import * as path from 'path';
import * as fs from 'fs';
import * as vscode from 'vscode';

export interface CompatibleJava {
    executable: string;
    version: number;
    description: string;
}

export async function findCompatibleJavas(context: vscode.ExtensionContext): Promise<CompatibleJava[]> {
    const additionalLocations: string[] = [];
    
    // Look for previously downloaded Java by the extension
    const storagePath = context.globalStorageUri.fsPath;
    if (fs.existsSync(storagePath)) {
        try {
            const items = fs.readdirSync(storagePath);
            for (const item of items) {
                if (item.startsWith('jre-') && fs.statSync(path.join(storagePath, item)).isDirectory()) {
                    additionalLocations.push(path.join(storagePath, item));
                }
            }
        } catch (e) {
            // Ignore fs errors when reading storage path
        }
    }

    // Search for all runtimes and parse their versions
    const runtimes = await findRuntimes({ 
        withVersion: true,
        additionalLocations
    });
    
    const compatible: CompatibleJava[] = [];
    const binExt = os.platform() === 'win32' ? '.exe' : '';

    for (const rt of runtimes) {
        if (rt.version && rt.version.major >= 21) {
            const executable = path.join(rt.homedir, 'bin', `java${binExt}`);

            if (fs.existsSync(executable)) {
                compatible.push({
                    executable,
                    version: rt.version.major,
                    description: rt.homedir
                });
            }
        }
    }

    // Deduplicate by executable path
    const unique = new Map<string, CompatibleJava>();
    for (const java of compatible) {
        unique.set(java.executable, java);
    }

    return Array.from(unique.values()).sort((a, b) => b.version - a.version);
}
