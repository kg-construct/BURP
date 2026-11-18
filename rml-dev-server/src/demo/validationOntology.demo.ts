import * as path from 'path';
import {By, Key, until} from 'vscode-extension-tester';
import {DemoContext, focusTokenOnLine, moveCursorTo, prepareDemoEnvironment, restoreMappingFile} from './helpers';
import {DemoRecorder} from './recorder';

describe('Demo: Ontology Validation (Predicate & Datatype Warning)', function () {
    this.timeout(120000);
    let ctx: DemoContext;

    before(async function () {
        this.timeout(60000);
        const fixtureMapping = path.resolve(__dirname, '../../src/demo/mappings/validation-ontology/mapping.ttl');
        ctx = await prepareDemoEnvironment(fixtureMapping);
    });

    after(function () {
        restoreMappingFile(ctx);
    });

    it('records hovering and fixing predicate (ex:birthday -> ex:age) and datatype (xsd:string -> xsd:integer)', async function () {
        this.timeout(90000);
        const {driver, demosDir} = ctx;

        // Scroll to the bottom of the 28-line mapping so ex:birthday and rml:datatype are in view
        try {
            const viewLines = await driver.wait(until.elementLocated(By.css('.monaco-editor .view-lines')), 10000);
            await viewLines.click();
            const inputs = await driver.findElements(
                By.css('.monaco-editor .native-edit-context, .monaco-editor textarea.inputarea')
            );
            const activeInput = inputs.length > 0 ? inputs[0] : await driver.switchTo().activeElement();
            await activeInput.sendKeys(Key.chord(Key.CONTROL, Key.END));
            await driver.sleep(500);
        } catch {
            // Ignore if scroll fallback is not needed
        }

        const predicateSpan = await driver.wait(
            until.elementLocated(
                By.xpath("//div[contains(@class, 'view-line') and not(contains(@class, 'view-lines')) and contains(., 'birthday')]//span[not(*) and contains(., 'birthday')]")
            ),
            10000
        );

        const recorder = new DemoRecorder(driver, 'validation-ontology', demosDir);
        await recorder.start();

        try {
            recorder.mark('Ontology predicate warning visible on ex:birthday');
            await driver.sleep(800);

            // 1. Hover over ex:birthday to show predicate warning (hold long enough to read)
            await moveCursorTo(driver, predicateSpan);
            await driver.actions().move({origin: predicateSpan}).perform();
            const predHover = await driver.wait(until.elementLocated(By.css('.monaco-hover')), 10000);
            recorder.mark('Predicate warning tooltip visible', await predHover.getRect());
            await driver.sleep(3200);

            // 2. Fix ex:birthday -> ex:age
            let activeInput = await focusTokenOnLine(driver, 'birthday', 'birthday');
            await activeInput.sendKeys(Key.END, Key.ARROW_LEFT, Key.ARROW_LEFT);
            for (let i = 0; i < 'birthday'.length; i++) {
                await activeInput.sendKeys(Key.BACK_SPACE);
            }
            await activeInput.sendKeys('age');
            recorder.mark('Fixed predicate to ex:age');
            await driver.sleep(1500);

            // 3. Hover over rml:datatype warning (xsd:string incompatible with xsd:integer)
            const datatypeSpan = await driver.wait(
                until.elementLocated(
                    By.xpath("//div[contains(@class, 'view-line') and not(contains(@class, 'view-lines')) and contains(., 'datatype')]//span[not(*) and contains(., 'string')]")
                ),
                10000
            );
            await moveCursorTo(driver, datatypeSpan);
            await driver.actions().move({origin: datatypeSpan}).perform();
            const dtHover = await driver.wait(until.elementLocated(By.css('.monaco-hover')), 10000);
            recorder.mark('Datatype warning tooltip visible', await dtHover.getRect());
            await driver.sleep(3000);

            // 4. Remove `string` -> trigger autocompletion -> filter `int` -> accept xsd:integer
            activeInput = await focusTokenOnLine(driver, 'datatype', 'string');
            await activeInput.sendKeys(Key.END);
            for (let i = 0; i < 'string'.length; i++) {
                await activeInput.sendKeys(Key.BACK_SPACE);
                await driver.sleep(90);
            }
            await driver.sleep(300);
            await activeInput.sendKeys(Key.chord(Key.CONTROL, Key.SPACE));
            const suggestWidget = await driver.wait(until.elementLocated(By.css('.suggest-widget.visible')), 10000);
            recorder.mark('Datatype completion suggestions visible', await suggestWidget.getRect());
            await driver.sleep(900);
            await activeInput.sendKeys('int');
            await driver.sleep(900);
            await activeInput.sendKeys(Key.ENTER);
            recorder.mark('Accepted xsd:integer from completion');
            await driver.sleep(1600);
        } finally {
            const output = await recorder.stop();
            console.log('Demo recorded:', output);
        }
    });
});
