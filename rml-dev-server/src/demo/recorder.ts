import {spawnSync} from 'child_process';
import * as fs from 'fs';
import * as path from 'path';
import {WebDriver} from 'vscode-extension-tester';

export interface Rect {
    x: number;
    y: number;
    width: number;
    height: number;
}

export interface AnnotationMark {
    timeSec: number;
    label: string;
    rect?: Rect;
}

export interface RecorderOptions {
    fps?: number;
    crop?: Rect;
    generateGif?: boolean;
}

function toEven(n: number): number {
    const floored = Math.floor(n);
    return floored % 2 === 0 ? floored : floored - 1;
}

function detectVideoEncoder(): string {
    const res = spawnSync('ffmpeg', ['-hide_banner', '-encoders'], {encoding: 'utf8'});
    const out = res.stdout || '';
    for (const candidate of ['libx264', 'libopenh264', 'libvpx_vp9', 'mpeg4']) {
        if (out.includes(candidate)) {
            return candidate;
        }
    }
    return 'mpeg4';
}

export class DemoRecorder {
    private readonly fps: number;
    private readonly generateGif: boolean;
    private readonly mp4Path: string;
    private readonly gifPath: string;
    private readonly annotationsPath: string;
    private readonly framesDir: string;

    private captureTimer: NodeJS.Timeout | null = null;
    private capturingFrame = false;
    private frameIndex = 0;
    private startTimeMs = 0;
    private readonly marks: AnnotationMark[] = [];

    constructor(
        private readonly driver: WebDriver,
        private readonly name: string,
        private readonly outputDir: string,
        private readonly options: RecorderOptions = {}
    ) {
        this.fps = options.fps ?? 8;
        this.generateGif = options.generateGif ?? true;

        this.mp4Path = path.join(outputDir, `${name}.mp4`);
        this.gifPath = path.join(outputDir, `${name}.gif`);
        this.annotationsPath = path.join(outputDir, `${name}.annotations.json`);
        this.framesDir = path.join(outputDir, `.frames-${name}`);
    }

    public async start(): Promise<void> {
        fs.mkdirSync(this.outputDir, {recursive: true});
        fs.rmSync(this.framesDir, {recursive: true, force: true});
        fs.mkdirSync(this.framesDir, {recursive: true});

        this.marks.length = 0;
        this.frameIndex = 0;
        this.startTimeMs = Date.now();

        const intervalMs = Math.max(50, Math.round(1000 / this.fps));
        this.captureTimer = setInterval(() => {
            if (this.capturingFrame) {
                return;
            }
            this.capturingFrame = true;
            const idx = this.frameIndex++;
            this.driver
                .takeScreenshot()
                .then((pngBase64) => {
                    const framePath = path.join(this.framesDir, `frame-${String(idx).padStart(5, '0')}.png`);
                    fs.writeFileSync(framePath, pngBase64, 'base64');
                })
                .catch(() => {
                    // Ignore transient screenshot errors during window transitions
                })
                .finally(() => {
                    this.capturingFrame = false;
                });
        }, intervalMs);
    }

    public mark(label: string, rect?: Rect): void {
        const timeSec = Number(((Date.now() - this.startTimeMs) / 1000).toFixed(3));
        this.marks.push({timeSec, label, rect});
    }

    public async stop(): Promise<{ mp4Path: string; gifPath?: string; annotationsPath: string }> {
        if (this.captureTimer) {
            clearInterval(this.captureTimer);
            this.captureTimer = null;
        }
        while (this.capturingFrame) {
            await new Promise((resolve) => setTimeout(resolve, 50));
        }

        fs.writeFileSync(this.annotationsPath, JSON.stringify(this.marks, null, 2), 'utf8');

        if (this.frameIndex > 0) {
            this.encodeMp4();
            if (this.generateGif && fs.existsSync(this.mp4Path)) {
                this.encodeGif();
            }
        }

        fs.rmSync(this.framesDir, {recursive: true, force: true});

        return {
            mp4Path: this.mp4Path,
            gifPath: this.generateGif ? this.gifPath : undefined,
            annotationsPath: this.annotationsPath
        };
    }

    private encodeMp4(): void {
        const cropFilter = this.options.crop
            ? `,crop=${toEven(this.options.crop.width)}:${toEven(this.options.crop.height)}:${Math.round(this.options.crop.x)}:${Math.round(this.options.crop.y)}`
            : '';

        const encoder = detectVideoEncoder();
        const result = spawnSync(
            'ffmpeg',
            [
                '-y',
                '-framerate',
                String(this.fps),
                '-i',
                path.join(this.framesDir, 'frame-%05d.png'),
                '-vf',
                `pad=ceil(iw/2)*2:ceil(ih/2)*2${cropFilter}`,
                '-c:v',
                encoder,
                '-pix_fmt',
                'yuv420p',
                '-an',
                this.mp4Path
            ],
            {stdio: 'pipe'}
        );

        if (result.status !== 0) {
            throw new Error(`ffmpeg screenshot encoding failed: ${result.stderr?.toString()}`);
        }
    }

    private encodeGif(): void {
        const result = spawnSync(
            'ffmpeg',
            [
                '-y',
                '-i',
                this.mp4Path,
                '-vf',
                'fps=15,scale=960:-1:flags=lanczos,split[s0][s1];[s0]palettegen=stats_mode=diff[p];[s1][p]paletteuse=dither=bayer:bayer_scale=3',
                this.gifPath
            ],
            {stdio: 'pipe'}
        );

        if (result.status !== 0) {
            throw new Error(`ffmpeg gif encoding failed: ${result.stderr?.toString()}`);
        }
    }
}
