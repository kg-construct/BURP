import {By, until} from 'vscode-extension-tester';
import {clickEditorTitleAction, DemoContext, prepareDemoEnvironment, restoreMappingFile} from './helpers';
import {DemoRecorder} from './recorder';

describe('Demo: Inline Evaluation & Iteration Stepping', function () {
    this.timeout(120000);
    let ctx: DemoContext;

    before(async function () {
        this.timeout(60000);
        ctx = await prepareDemoEnvironment('coherent_stepping/mapping.ttl');
    });

    after(function () {
        restoreMappingFile(ctx);
    });

    it('records stepping through coherent joined inline evaluation iterations', async function () {
        this.timeout(90000);
        const {driver, demosDir} = ctx;

        // Scroll past the first ~14 lines so subjectMap, predicateObjectMap, and joined TriplesMap2 are in view
        try {
            const scrollable = await driver.wait(
                until.elementLocated(By.css('.monaco-scrollable-element.editor-scrollable')),
                10000
            );
            await driver.executeScript('arguments[0].scrollTop = 260;', scrollable);
            await driver.sleep(600);
        } catch {
            // Ignore if scroll container selector differs
        }

        const recorder = new DemoRecorder(driver, 'inlay-hints-stepping', demosDir);
        await recorder.start();

        try {
            recorder.mark('Iteration 1 inline evaluation (joined)');
            await driver.sleep(1500);

            const nextBtn = await clickEditorTitleAction(driver, 'Next Inline Iteration');
            recorder.mark('Step to next iteration', await nextBtn.getRect());
            await driver.sleep(1500);

            await clickEditorTitleAction(driver, 'Next Inline Iteration');
            recorder.mark('Step to third iteration');
            await driver.sleep(1500);

            const prevBtn = await clickEditorTitleAction(driver, 'Previous Inline Iteration');
            recorder.mark('Step back to previous iteration', await prevBtn.getRect());
            await driver.sleep(1500);
        } finally {
            const output = await recorder.stop();
            console.log('Demo recorded:', output);
        }
    });
});
