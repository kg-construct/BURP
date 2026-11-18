import {By, until} from 'vscode-extension-tester';
import {DemoContext, moveCursorTo, prepareDemoEnvironment, restoreMappingFile} from './helpers';
import {DemoRecorder} from './recorder';

describe('Demo: RML & FNML Validation Diagnostics', function () {
    this.timeout(120000);
    let ctx: DemoContext;

    before(async function () {
        this.timeout(60000);
        ctx = await prepareDemoEnvironment('validate/invalid-param.ttl');
    });

    after(function () {
        restoreMappingFile(ctx);
    });

    it('records hovering over an FNML parameter validation diagnostic', async function () {
        this.timeout(90000);
        const {driver, demosDir} = ctx;

        // Target the exact leaf span containing `nonExistentParam` (not the full-width .view-line)
        const invalidSpan = await driver.wait(
            until.elementLocated(By.xpath("//div[contains(@class, 'view-line') and not(contains(@class, 'view-lines'))]//span[not(*) and contains(., 'nonExistentParam')]")),
            10000
        );

        const recorder = new DemoRecorder(driver, 'validation-fnml', demosDir);
        await recorder.start();

        try {
            recorder.mark('Invalid FNML parameter highlighted', await invalidSpan.getRect());
            await driver.sleep(900);

            await moveCursorTo(driver, invalidSpan);
            await driver.actions().move({origin: invalidSpan}).perform();
            const hoverWidget = await driver.wait(
                until.elementLocated(By.css('.monaco-hover')),
                10000
            );
            recorder.mark('Diagnostic hover tooltip visible', await hoverWidget.getRect());
            await driver.sleep(2500);
        } finally {
            const output = await recorder.stop();
            console.log('Demo recorded:', output);
        }
    });
});
