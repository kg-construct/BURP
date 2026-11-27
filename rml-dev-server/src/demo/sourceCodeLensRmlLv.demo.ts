import * as path from 'path';
import {By, until, WebView} from 'vscode-extension-tester';
import {
    clickWithCursor,
    DemoContext,
    hideCursor,
    moveCursorToPoint,
    prepareDemoEnvironment,
    restoreMappingFile
} from './helpers';
import {DemoRecorder} from './recorder';

describe('Demo: RML-LV & Original Source CodeLens', function () {
    this.timeout(120000);
    let ctx: DemoContext;

    before(async function () {
        this.timeout(60000);
        const fixtureMapping = path.resolve(__dirname, '../../src/demo/mappings/source-codelens-rml-lv/mapping.ttl');
        ctx = await prepareDemoEnvironment(fixtureMapping, {
            windowWidth: 1360,
            windowHeight: 860
        });
    });

    after(function () {
        restoreMappingFile(ctx);
    });

    async function closeSideViewTabs(driver: DemoContext['driver']): Promise<void> {
        const tabs = await driver.findElements(By.css('.tabs-container .tab'));
        for (const tab of tabs) {
            const label = ((await tab.getAttribute('aria-label')) || '') + ' ' + ((await tab.getText()) || '');
            if (!label.includes('mapping.ttl')) {
                const closeBtns = await tab.findElements(By.css('a.codicon-close'));
                if (closeBtns.length > 0) {
                    await clickWithCursor(driver, closeBtns[0]);
                    await driver.sleep(250);
                }
            }
        }
    }

    it('records previewing JSON source first, then RML-LV view, scrolling, and returning cleanly', async function () {
        this.timeout(90000);
        const {driver, demosDir} = ctx;

        const recorder = new DemoRecorder(driver, 'source-codelens-rml-lv', demosDir);
        const webview = new WebView();
        await recorder.start();

        try {
            // Leave mapping visible for 1 second at start
            recorder.mark('RML-LV mapping visible');
            await driver.sleep(1100);

            // 1. Click "View Logical Source Data" CodeLens on :jsonSource first
            const srcLens = await driver.wait(
                until.elementLocated(By.xpath("//a[contains(., 'View Logical Source Data')]")),
                15000
            );
            recorder.mark('Click View Logical Source Data CodeLens', await srcLens.getRect());
            await clickWithCursor(driver, srcLens);

            await driver.sleep(800);
            const srcIframe = await driver.findElement(By.css('iframe.webview.ready'));
            const srcRect = await srcIframe.getRect();
            await moveCursorToPoint(driver, srcRect.x + srcRect.width / 2, srcRect.y + srcRect.height / 2);
            await driver.sleep(1100);
            recorder.mark('JSON source preview open');

            await closeSideViewTabs(driver);
            await driver.sleep(600);

            // 2. Click "View Logical View Data" CodeLens on :jsonView
            const lvLens = await driver.wait(
                until.elementLocated(By.xpath("//a[contains(., 'View Logical View Data')]")),
                15000
            );
            recorder.mark('Click View Logical View Data CodeLens', await lvLens.getRect());
            await clickWithCursor(driver, lvLens);

            await driver.sleep(800);
            const lvIframe = await driver.findElement(By.css('iframe.webview.ready'));
            const lvRect = await lvIframe.getRect();
            await moveCursorToPoint(driver, lvRect.x + lvRect.width / 2, lvRect.y + lvRect.height / 2);

            await webview.switchToFrame(30000);
            await driver.sleep(900);
            recorder.mark('RML-LV data table open');

            // Smooth scroll inside the RML-LV table
            await driver.executeScript('window.scrollBy({ top: 180, behavior: "smooth" });');
            await driver.sleep(1000);
            await driver.executeScript('window.scrollBy({ top: -180, behavior: "smooth" });');
            await driver.sleep(900);
            await webview.switchBack();

            // Close RML-LV view and return to initial scroll position so it loops cleanly
            await closeSideViewTabs(driver);
            await hideCursor(driver);
            try {
                const scrollable = await driver.findElement(By.css('.monaco-scrollable-element.editor-scrollable'));
                await driver.executeScript('arguments[0].scrollTop = 0;', scrollable);
            } catch {
                // Ignore if already at top
            }
            recorder.mark('Returned to initial mapping state');
            await driver.sleep(900);
        } finally {
            try {
                await webview.switchBack();
            } catch {
                // Already switched back
            }
            const output = await recorder.stop();
            console.log('Demo recorded:', output);
        }
    });
});
