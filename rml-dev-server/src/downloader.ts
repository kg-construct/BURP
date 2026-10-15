import * as fs from 'fs';
import * as vscode from 'vscode';
import axios from 'axios';

export interface DownloadFileOptions {
    title: string;
    outputChannel: vscode.OutputChannel;
    cancellable?: boolean;
    maxRetries?: number;
}

export async function downloadFile(
    url: string,
    destPath: string,
    options: DownloadFileOptions
): Promise<void> {
    const maxRetries = options.maxRetries ?? 3;
    const cancellable = options.cancellable ?? true;

    await vscode.window.withProgress({
        location: vscode.ProgressLocation.Notification,
        title: options.title,
        cancellable
    }, async (progress, token) => {
        const abortController = new AbortController();
        token.onCancellationRequested(() => {
            abortController.abort();
        });

        for (let attempt = 1; attempt <= maxRetries; attempt++) {
            if (token.isCancellationRequested) {
                cleanupFile(destPath);
                throw new Error('Download cancelled by user.');
            }

            try {
                options.outputChannel.appendLine(`[Downloader] Starting download from ${url}... (Attempt ${attempt}/${maxRetries})`);
                const response = await axios({
                    url,
                    method: 'GET',
                    responseType: 'stream',
                    signal: abortController.signal,
                    onDownloadProgress: (progressEvent) => {
                        if (progressEvent.total) {
                            const percent = Math.round((progressEvent.loaded / progressEvent.total) * 100);
                            progress.report({message: `${percent}%`});
                        }
                    }
                });

                const writer = fs.createWriteStream(destPath);
                response.data.pipe(writer);

                await new Promise<void>((resolve, reject) => {
                    writer.on('finish', () => resolve());
                    writer.on('error', (err) => reject(err));
                });

                return;
            } catch (err: any) {
                cleanupFile(destPath);
                if (axios.isCancel(err) || err.name === 'AbortError' || token.isCancellationRequested) {
                    options.outputChannel.appendLine('[Downloader] Download cancelled.');
                    throw new Error('Download cancelled by user.');
                }
                options.outputChannel.appendLine(`[Downloader] Attempt ${attempt} failed: ${err.message}`);
                if (attempt === maxRetries) {
                    throw err;
                }
                await new Promise((r) => setTimeout(r, 2000));
            }
        }
    });
}

function cleanupFile(filePath: string): void {
    if (fs.existsSync(filePath)) {
        try {
            fs.unlinkSync(filePath);
        } catch {
            // ignore cleanup failure
        }
    }
}
