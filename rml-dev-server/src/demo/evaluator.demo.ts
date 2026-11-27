import {By, Key, until, WebView} from 'vscode-extension-tester';
import {
    clickWithCursor,
    closeSidebarIfOpen,
    DemoContext,
    hideCursor,
    moveCursorTo,
    moveCursorToPoint,
    prepareDemoEnvironment,
    restoreMappingFile
} from './helpers';
import {DemoRecorder} from './recorder';

describe('Demo: Evaluator & Value Inspector', function () {
    this.timeout(120000);
    let ctx: DemoContext;

    before(async function () {
        this.timeout(60000);
        ctx = await prepareDemoEnvironment('trace_template_null/mapping.ttl', {
            windowWidth: 1280,
            windowHeight: 900
        });
    });

    after(async function () {
        await closeSidebarIfOpen(ctx.driver);
        restoreMappingFile(ctx);
    });

    it('records opening evaluator, focusing template node, all data with warning, and custom reference values', async function () {
        this.timeout(90000);
        const {driver, demosDir} = ctx;

        const webview = new WebView();
        const recorder = new DemoRecorder(driver, 'evaluator-inspector', demosDir);
        await recorder.start();

        try {
            const activityIcon = await driver.wait(
                until.elementLocated(By.xpath("//a[contains(@aria-label, 'RML Dev Server')]")),
                10000
            );
            recorder.mark('Open Evaluator & Value Inspector sidebar', await activityIcon.getRect());
            await clickWithCursor(driver, activityIcon);
            await driver.sleep(700);

            // Widen the primary sidebar (~110px) so evaluation results are easy to read
            try {
                const sashes = await driver.findElements(By.css('.part.sidebar + .sash.vertical, .monaco-sash.vertical'));
                if (sashes.length > 0) {
                    await driver.actions().dragAndDrop(sashes[0], {x: 110, y: 0}).perform();
                    await driver.sleep(300);
                }
            } catch {
                // Ignore if sash drag is not available
            }

            // Move virtual cursor to the template node in the editor and click it so Evaluator populates
            const templateToken = await driver.wait(
                until.elementLocated(
                    By.xpath("//div[contains(@class, 'view-line') and not(contains(@class, 'view-lines')) and contains(., 'template')]//span[not(*) and contains(., 'person')]")
                ),
                10000
            );
            await clickWithCursor(driver, templateToken);
            recorder.mark('Focus template node (Sample Iteration 0: Alice_Smith)');
            await driver.sleep(2000);

            // Capture outer iframe position so we can move the visible outer cursor over webview elements
            const iframeEl = await driver.findElement(By.css('iframe.webview.ready'));
            const iframeRect = await iframeEl.getRect();

            await webview.switchToFrame(10000);

            // Move virtual cursor to the data context dropdown and switch to All Data
            const selectEl = await driver.wait(until.elementLocated(By.css('select.bk-select')), 10000);
            const selectRect = await selectEl.getRect();
            await webview.switchBack();
            await moveCursorToPoint(
                driver,
                iframeRect.x + selectRect.x + selectRect.width / 2,
                iframeRect.y + selectRect.y + selectRect.height / 2
            );
            await driver.sleep(350);
            await webview.switchToFrame(10000);
            const selectElFresh = await driver.wait(until.elementLocated(By.css('select.bk-select')), 10000);
            await driver.executeScript(
                "arguments[0].click(); arguments[0].value = 'all'; arguments[0].dispatchEvent(new Event('change', { bubbles: true }));",
                selectElFresh
            );
            await driver.sleep(800);

            // Move virtual cursor over the 2nd iteration warning / "(no terms generated)" card (+28px right)
            const iterCards = await driver.findElements(By.css('.bk-iteration-card'));
            if (iterCards.length >= 2) {
                const warnRect = await iterCards[1].getRect();
                await webview.switchBack();
                await moveCursorToPoint(
                    driver,
                    iframeRect.x + warnRect.x + warnRect.width / 2 + 28,
                    iframeRect.y + warnRect.y + warnRect.height / 2
                );
                recorder.mark('Pause over 2nd record warning (no terms generated)', warnRect);
                await driver.sleep(2200);
                await webview.switchToFrame(10000);
            } else {
                await driver.sleep(2000);
            }

            // Move virtual cursor to the data context dropdown and switch to Reference Field Values
            const selectEl2 = await driver.wait(until.elementLocated(By.css('select.bk-select')), 10000);
            const selectRect2 = await selectEl2.getRect();
            await webview.switchBack();
            await moveCursorToPoint(
                driver,
                iframeRect.x + selectRect2.x + selectRect2.width / 2,
                iframeRect.y + selectRect2.y + selectRect2.height / 2
            );
            await driver.sleep(350);
            await webview.switchToFrame(10000);
            const selectEl2Fresh = await driver.wait(until.elementLocated(By.css('select.bk-select')), 10000);
            await driver.executeScript(
                "arguments[0].click(); arguments[0].value = 'referenceFields'; arguments[0].dispatchEvent(new Event('change', { bubbles: true }));",
                selectEl2Fresh
            );
            await driver.sleep(700);

            const inputs = await driver.findElements(By.css('.bk-field-grid input'));
            if (inputs.length >= 2) {
                const rect0 = await inputs[0].getRect();
                await webview.switchBack();
                await moveCursorToPoint(driver, iframeRect.x + rect0.x + rect0.width / 2, iframeRect.y + rect0.y + rect0.height / 2);
                await driver.sleep(250);
                await webview.switchToFrame(10000);
                await inputs[0].click();
                await inputs[0].sendKeys(Key.chord(Key.CONTROL, 'a'), 'Ada');

                const rect1 = await inputs[1].getRect();
                await webview.switchBack();
                await moveCursorToPoint(driver, iframeRect.x + rect1.x + rect1.width / 2, iframeRect.y + rect1.y + rect1.height / 2);
                await driver.sleep(250);
                await webview.switchToFrame(10000);
                await inputs[1].click();
                await inputs[1].sendKeys(Key.chord(Key.CONTROL, 'a'), 'Lovelace');
            }
            recorder.mark('Custom Reference Field Values entered');
            await driver.sleep(800);

            const applyBtn = await driver.findElement(By.xpath("//button[contains(., 'Apply & Run')]"));
            const applyRect = await applyBtn.getRect();
            await webview.switchBack();
            await moveCursorToPoint(driver, iframeRect.x + applyRect.x + applyRect.width / 2, iframeRect.y + applyRect.y + applyRect.height / 2);
            await driver.sleep(300);
            await webview.switchToFrame(10000);
            await driver.executeScript('arguments[0].click();', applyBtn);
            await driver.sleep(900);

            // Hover over generated result in Evaluator
            const cards = await driver.findElements(By.css('.bk-iteration-card'));
            if (cards.length > 0) {
                const resRect = await cards[0].getRect();
                await webview.switchBack();
                await moveCursorToPoint(driver, iframeRect.x + resRect.x + resRect.width / 2, iframeRect.y + resRect.y + resRect.height / 2);
                recorder.mark('Custom reference result in Evaluator', resRect);
                await driver.sleep(1400);
            } else {
                await webview.switchBack();
            }

            // Move cursor onto `{$.firstName} -> "Ada"` inline hint on the template line
            const adaHints = await driver.findElements(
                By.xpath("//div[contains(@class, 'view-line') and not(contains(@class, 'view-lines')) and contains(., 'template')]//span[not(*) and contains(., 'Ada')]")
            );
            const targetHint =
                adaHints.length > 0
                    ? adaHints[0]
                    : await driver.wait(
                        until.elementLocated(
                            By.xpath("//div[contains(@class, 'view-line') and not(contains(@class, 'view-lines')) and contains(., 'template')]//span[not(*) and contains(., 'firstName')]")
                        ),
                        10000
                    );
            await moveCursorTo(driver, targetHint);
            await driver.actions().move({origin: targetHint}).perform();
            recorder.mark('Hover {$.firstName -> "Ada"} inline hint in mapping');
            await driver.sleep(2000);
            await hideCursor(driver);
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
