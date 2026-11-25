import * as path from 'path';
import {By, until, WebView} from 'vscode-extension-tester';
import {
    clickEditorTitleAction,
    closeMatchingEditors,
    DemoContext,
    hideCursor,
    prepareDemoEnvironment,
    restoreMappingFile
} from '../helpers';
import {DemoRecorder} from '../recorder';

describe('Manual Demo: Quad Provenance & Dataflow Tracing', function () {
    this.timeout(120000);
    let ctx: DemoContext;

    before(async function () {
        this.timeout(60000);
        const fixtureMapping = path.resolve(__dirname, '../../../src/demo/mappings/trace-provenance/mapping.ttl');
        ctx = await prepareDemoEnvironment(fixtureMapping, {
            windowWidth: 1440,
            windowHeight: 900
        });
    });

    after(function () {
        restoreMappingFile(ctx);
    });

    it('opens Trace view and records 12s for manual interaction', async function () {
        this.timeout(90000);
        const {driver, demosDir} = ctx;

        await clickEditorTitleAction(driver, 'Run Mapping');
        await hideCursor(driver);

        const webview = new WebView();
        const recorder = new DemoRecorder(driver, 'trace-provenance', demosDir, {fps: 12});
        let recordingStarted = false;

        try {
            await webview.switchToFrame(30000);
            const traceBtn = await driver.wait(
                until.elementLocated(By.css('button[title="Trace this quad"]')),
                30000
            );
            await driver.executeScript('arguments[0].click();', traceBtn);
            await webview.switchBack();
            await driver.sleep(1000);

            await closeMatchingEditors(
                (title) => (title.includes('Run') || title.includes('Execution')) && !title.includes('Trace')
            );
            await driver.sleep(800);

            // Wait until Trace SVG is rendered before starting the 12s manual recording window
            const frames = await driver.wait(async () => {
                const list = await driver.findElements(By.css('iframe.webview.ready'));
                return list.length > 0 ? list : null;
            }, 15000);
            if (!frames || frames.length === 0) {
                throw new Error('Trace webview iframe not found');
            }
            await driver.switchTo().frame(frames[frames.length - 1]);
            const activeFrame = await driver.wait(until.elementLocated(By.css('iframe#active-frame')), 15000);
            await driver.switchTo().frame(activeFrame);
            await driver.wait(until.elementLocated(By.css('svg')), 20000);
            await webview.switchBack();

            console.log('>>> Trace view ready! Recording 12 seconds for manual zoom/click/pan... <<<');
            await recorder.start();
            recordingStarted = true;
            await driver.sleep(16000);
        } finally {
            try {
                await webview.switchBack();
            } catch {
                // Already switched back
            }
            if (recordingStarted) {
                const output = await recorder.stop();
                console.log('Demo recorded:', output);
            }
        }
    });
});
