import * as path from 'path';
import {By, until, WebView} from 'vscode-extension-tester';
import {
    clickEditorTitleAction,
    DemoContext,
    hideCursor,
    moveCursorToPoint,
    prepareDemoEnvironment,
    restoreMappingFile
} from './helpers';
import {DemoRecorder} from './recorder';

describe('Demo: Run Mapping & RDF Visualizer', function () {
    this.timeout(120000);
    let ctx: DemoContext;

    before(async function () {
        this.timeout(60000);
        const fixtureMapping = path.resolve(__dirname, '../../src/demo/mappings/run-and-visualizer/mapping.ttl');
        ctx = await prepareDemoEnvironment(fixtureMapping, {
            windowWidth: 1440,
            windowHeight: 900
        });
    });

    after(function () {
        restoreMappingFile(ctx);
    });

    it('records executing a mapping and viewing Group by Subject, Group by Iteration, and Graph modes', async function () {
        this.timeout(90000);
        const {driver, demosDir} = ctx;

        const recorder = new DemoRecorder(driver, 'run-and-visualizer', demosDir);
        const webview = new WebView();
        await recorder.start();

        try {
            recorder.mark('Mapping file ready (RMLTC0002a-JSON adapted)');
            await driver.sleep(1200);

            recorder.mark('Click Run Mapping');
            await clickEditorTitleAction(driver, 'Run Mapping');
            await hideCursor(driver);

            const iframeEl = await driver.wait(until.elementLocated(By.css('iframe.webview.ready')), 30000);
            const iframeRect = await iframeEl.getRect();

            await webview.switchToFrame(30000);
            await driver.wait(until.elementLocated(By.css('.bk-table-custom tbody tr')), 30000);
            recorder.mark('Flat output table rendered');
            await driver.sleep(1400);

            // 1. Click Group by Subject
            const subjectBtn = await webview.findWebElement(
                By.xpath("//button[contains(@class, 'bk-segmented-btn') and contains(., 'Group by Subject')]")
            );
            const subjBtnRect = await subjectBtn.getRect();
            await webview.switchBack();
            await moveCursorToPoint(driver, iframeRect.x + subjBtnRect.x + subjBtnRect.width / 2, iframeRect.y + subjBtnRect.y + subjBtnRect.height / 2);
            await driver.sleep(300);
            await webview.switchToFrame(10000);
            await driver.executeScript('arguments[0].click();', subjectBtn);
            await driver.wait(until.elementLocated(By.css('.bk-subject-group-row')), 10000);
            recorder.mark('Grouped by Subject');
            await driver.sleep(1400);

            // 2. Click Group by Iteration
            const iterationBtn = await webview.findWebElement(
                By.xpath("//button[contains(@class, 'bk-segmented-btn') and contains(., 'Group by Iteration')]")
            );
            const iterBtnRect = await iterationBtn.getRect();
            await webview.switchBack();
            await moveCursorToPoint(driver, iframeRect.x + iterBtnRect.x + iterBtnRect.width / 2, iframeRect.y + iterBtnRect.y + iterBtnRect.height / 2);
            await driver.sleep(300);
            await webview.switchToFrame(10000);
            await driver.executeScript('arguments[0].click();', iterationBtn);
            await driver.wait(until.elementLocated(By.css('.bk-subject-group-row')), 10000);
            recorder.mark('Grouped by Iteration');
            await driver.sleep(900);

            try {
                const collapseBtn = await webview.findWebElement(
                    By.xpath("//button[contains(@class, 'bk-col-filter-btn') and contains(., 'Collapse All')]")
                );
                await driver.executeScript('arguments[0].click();', collapseBtn);
                await driver.sleep(400);
            } catch {
                // Ignore if already collapsed
            }

            const firstIterRow = await webview.findWebElement(By.css('.bk-subject-group-row'));
            const firstRowRect = await firstIterRow.getRect();
            await webview.switchBack();
            await moveCursorToPoint(driver, iframeRect.x + firstRowRect.x + 80, iframeRect.y + firstRowRect.y + firstRowRect.height / 2);
            await driver.sleep(300);
            await webview.switchToFrame(10000);
            await driver.executeScript('arguments[0].click();', firstIterRow);
            recorder.mark('First iteration expanded');
            await driver.sleep(1400);

            // 3. Switch to Graph view
            const graphBtn = await webview.findWebElement(
                By.xpath("//button[contains(@class, 'bk-segmented-btn') and contains(., 'Graph')]")
            );
            const graphBtnRect = await graphBtn.getRect();
            await webview.switchBack();
            await moveCursorToPoint(driver, iframeRect.x + graphBtnRect.x + graphBtnRect.width / 2, iframeRect.y + graphBtnRect.y + graphBtnRect.height / 2);
            await driver.sleep(300);
            await webview.switchToFrame(10000);
            await driver.executeScript('arguments[0].click();', graphBtn);
            await webview.switchBack();
            await hideCursor(driver);
            await driver.sleep(2200);
            recorder.mark('RDF Graph view rendered');
            await driver.sleep(1500);
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
