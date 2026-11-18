import * as path from 'path';
import {By, Key, until} from 'vscode-extension-tester';
import {DemoContext, focusTokenOnLine, prepareDemoEnvironment, restoreMappingFile} from './helpers';
import {DemoRecorder} from './recorder';

describe('Demo: FNML Parameter Completion', function () {
    this.timeout(120000);
    let ctx: DemoContext;

    before(async function () {
        this.timeout(60000);
        const fixtureMapping = path.resolve(__dirname, '../../src/demo/mappings/completion-fnml/mapping.ttl');
        ctx = await prepareDemoEnvironment(fixtureMapping);
    });

    after(function () {
        restoreMappingFile(ctx);
    });

    it('records FNML parameter autocompletion with grel:toUpperCase', async function () {
        this.timeout(90000);
        const {driver, demosDir} = ctx;

        // `rml:parameter grel:;` is on line 22 (col 28 right after `grel:`)
        const activeInput = await focusTokenOnLine(driver, 'parameter', 'grel:');
        await activeInput.sendKeys(Key.END, Key.ARROW_LEFT);
        await driver.sleep(600);

        const recorder = new DemoRecorder(driver, 'completion-fnml', demosDir);
        await recorder.start();

        try {
            recorder.mark('Cursor at rml:parameter grel:');
            await driver.sleep(900);

            recorder.mark('Trigger FNML parameter completion');
            await activeInput.sendKeys(Key.chord(Key.CONTROL, Key.SPACE));

            const suggestWidget = await driver.wait(
                until.elementLocated(By.css('.suggest-widget.visible')),
                10000
            );
            recorder.mark('grel:valueParam suggestion visible', await suggestWidget.getRect());
            await driver.sleep(1400);

            recorder.mark('Accept grel:valueParam');
            await activeInput.sendKeys(Key.ENTER);
            await driver.sleep(1600);
        } finally {
            const output = await recorder.stop();
            console.log('Demo recorded:', output);
        }
    });
});
