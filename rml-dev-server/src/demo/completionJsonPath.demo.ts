import {By, Key, until} from 'vscode-extension-tester';
import {DemoContext, focusToken, prepareDemoEnvironment, restoreMappingFile} from './helpers';
import {DemoRecorder} from './recorder';

describe('Demo: JSONPath Completion from Implicit Schema', function () {
    this.timeout(120000);
    let ctx: DemoContext;

    before(async function () {
        this.timeout(60000);
        ctx = await prepareDemoEnvironment('eval_alice/mapping.ttl');
    });

    after(function () {
        restoreMappingFile(ctx);
    });

    it('records JSONPath completion in template {} and reference ""', async function () {
        this.timeout(90000);
        const {driver, demosDir} = ctx;

        // 1. Pre-clear `$.age` in rml:reference "$.age" -> ""
        let activeInput = await focusToken(driver, '$.age');
        await activeInput.sendKeys(Key.END, Key.ARROW_LEFT);
        for (let i = 0; i < '$.age'.length; i++) {
            await activeInput.sendKeys(Key.BACK_SPACE);
        }

        // 2. Pre-clear `$.name` in rml:template "http://example.org/person/{$.name}" -> "{}"
        activeInput = await focusToken(driver, '$.name');
        await activeInput.sendKeys(Key.END, Key.ARROW_LEFT, Key.ARROW_LEFT);
        for (let i = 0; i < '$.name'.length; i++) {
            await activeInput.sendKeys(Key.BACK_SPACE);
        }
        await driver.sleep(600);

        const recorder = new DemoRecorder(driver, 'completion-jsonpath', demosDir);
        await recorder.start();

        try {
            recorder.mark('Empty {} in template and "" in reference');
            await driver.sleep(900);

            // Trigger completion inside `{}` in rml:template
            recorder.mark('Trigger JSONPath completion in template {}');
            await activeInput.sendKeys(Key.chord(Key.CONTROL, Key.SPACE));
            let suggestWidget = await driver.wait(until.elementLocated(By.css('.suggest-widget.visible')), 10000);
            recorder.mark('Template suggestions visible', await suggestWidget.getRect());
            await driver.sleep(1100);

            // Select $.name (2nd item after $.age) and accept
            await activeInput.sendKeys(Key.ARROW_DOWN);
            await driver.sleep(700);
            await activeInput.sendKeys(Key.ENTER);
            recorder.mark('Accepted $.name in template');
            await driver.sleep(1200);

            // Move cursor inside `""` on the rml:reference line
            activeInput = await focusToken(driver, '""');
            await activeInput.sendKeys(Key.END, Key.ARROW_LEFT);
            await driver.sleep(400);

            recorder.mark('Trigger JSONPath completion in reference ""');
            await activeInput.sendKeys(Key.chord(Key.CONTROL, Key.SPACE));
            suggestWidget = await driver.wait(until.elementLocated(By.css('.suggest-widget.visible')), 10000);
            recorder.mark('Reference suggestions visible', await suggestWidget.getRect());
            await driver.sleep(1100);

            // Accept $.age (1st item)
            await activeInput.sendKeys(Key.ENTER);
            recorder.mark('Accepted $.age in reference');
            await driver.sleep(1500);
        } finally {
            const output = await recorder.stop();
            console.log('Demo recorded:', output);
        }
    });
});
