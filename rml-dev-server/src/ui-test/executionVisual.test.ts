import * as fs from 'fs';
import * as path from 'path';
import * as assert from 'assert';
import {
    By,
    EditorView,
    until,
    VSBrowser,
    WebView
} from 'vscode-extension-tester';

describe('Execution & RDF Visualizer UI Tests', function () {
    this.timeout(120000);

    const screenshotsDir = path.resolve(__dirname, '../../../screenshots');
    const mappingPath = path.resolve(__dirname, '../../../src/test/resources/burp/rml_tc0009a/mapping.ttl');

    let browser: VSBrowser;
    let webview: WebView;

    async function saveScreenshot(name: string): Promise<void> {
        if (!fs.existsSync(screenshotsDir)) {
            fs.mkdirSync(screenshotsDir, { recursive: true });
        }
        const filePath = path.join(screenshotsDir, `${name}.png`);
        try {
            await webview.switchBack();
        } catch (ignored) {}
        await browser.driver.sleep(500);
        const pngBase64 = await browser.driver.takeScreenshot();
        fs.writeFileSync(filePath, pngBase64, 'base64');
        console.log(`Saved screenshot: ${filePath}`);
        try {
            await webview.switchToFrame(15000);
        } catch (ignored) {}
    }

    async function clickWebviewElement(element: any): Promise<void> {
        const driver = browser.driver;
        await driver.executeScript("arguments[0].scrollIntoView({block: 'center'}); arguments[0].click();", element);
    }

    before(async function () {
        this.timeout(60000);
        browser = VSBrowser.instance;
        await browser.waitForWorkbench();

        // Ensure screenshot directory exists
        if (!fs.existsSync(screenshotsDir)) {
            fs.mkdirSync(screenshotsDir, { recursive: true });
        }

        const editorView = new EditorView();
        try {
            await editorView.closeEditor('Walkthrough: Setup VS Code');
        } catch (ignored) {}

        try {
            const tab = await editorView.getTabByTitle('mapping.ttl');
            if (tab) {
                await tab.select();
            } else {
                await editorView.openEditor('mapping.ttl');
            }
        } catch (e) {
            console.log('Attempting openResources for mapping.ttl');
            await browser.openResources(mappingPath);
        }
        await browser.driver.sleep(2000);
    });

    it('executes mapping and renders flat table baseline screenshot', async function () {
        this.timeout(60000);
        const driver = browser.driver;

        // Run the mapping command by locating and clicking the play icon on the editor title bar
        const playBtn = await driver.wait(
            until.elementLocated(By.xpath("//a[contains(@class, 'codicon-play') or contains(@aria-label, 'Run Mapping') or contains(@title, 'Run Mapping')]")),
            15000
        );
        await playBtn.click();

        // Switch to the execution webview panel
        webview = new WebView();
        await webview.switchToFrame(30000);

        // Wait for execution to finish and rows to populate
        await driver.wait(until.elementLocated(By.css('.bk-table-custom tbody tr.bk-lv-tr')), 30000);
        await driver.sleep(1500);

        // Take Screenshot 1: Baseline flat table
        await saveScreenshot('01-execution-flat-table');

        const rows = await webview.findWebElements(By.css('.bk-table-custom tbody tr.bk-lv-tr'));
        assert.ok(rows.length >= 1, 'Expected at least 1 flat table row');
    });

    it('switches to Group by Subject, expands first row, and takes screenshot', async function () {
        this.timeout(30000);
        const driver = browser.driver;

        // Click "Group by Subject"
        const subjectBtn = await webview.findWebElement(
            By.xpath("//button[contains(@class, 'bk-segmented-btn') and contains(., 'Group by Subject')]")
        );
        await clickWebviewElement(subjectBtn);
        await driver.sleep(800);

        // Wait for subject group rows
        await driver.wait(until.elementLocated(By.css('.bk-subject-group-row')), 10000);

        // Collapse all groups first to ensure clean state
        try {
            const collapseBtn = await webview.findWebElement(
                By.xpath("//button[contains(@class, 'bk-col-filter-btn') and contains(., 'Collapse All')]")
            );
            await clickWebviewElement(collapseBtn);
            await driver.sleep(400);
        } catch (ignored) {}

        // Expand the first subject group row
        const groupRows = await webview.findWebElements(By.css('.bk-subject-group-row'));
        assert.ok(groupRows.length >= 1, 'Expected at least 1 subject group row');
        await clickWebviewElement(groupRows[0]);
        await driver.sleep(800);

        // Wait for expanded sub-rows to be present
        await driver.wait(until.elementLocated(By.css('.bk-sub-row')), 10000);
        await driver.sleep(600);

        // Take Screenshot 2: Group by Subject with first row expanded
        await saveScreenshot('02-group-by-subject-first-row');
    });

    it('switches to Group by Iteration, expands first row, and takes screenshot', async function () {
        this.timeout(30000);
        const driver = browser.driver;

        // Click "Group by Iteration"
        const iterationBtn = await webview.findWebElement(
            By.xpath("//button[contains(@class, 'bk-segmented-btn') and contains(., 'Group by Iteration')]")
        );
        await clickWebviewElement(iterationBtn);
        await driver.sleep(800);

        // Wait for iteration group rows (shows "Iteration" text inside the group row)
        await driver.wait(until.elementLocated(By.xpath("//tr[contains(@class, 'bk-subject-group-row') and contains(., 'Iteration')]")), 10000);

        // Collapse all groups first
        try {
            const collapseBtn = await webview.findWebElement(
                By.xpath("//button[contains(@class, 'bk-col-filter-btn') and contains(., 'Collapse All')]")
            );
            await clickWebviewElement(collapseBtn);
            await driver.sleep(400);
        } catch (ignored) {}

        // Expand the first iteration group row
        const groupRowsIter = await webview.findWebElements(By.css('.bk-subject-group-row'));
        assert.ok(groupRowsIter.length >= 1, 'Expected at least 1 iteration group row');
        await clickWebviewElement(groupRowsIter[0]);
        await driver.sleep(800);

        // Wait for expanded sub-rows to be present
        await driver.wait(until.elementLocated(By.css('.bk-sub-row')), 10000);
        await driver.sleep(600);

        // Take Screenshot 3: Group by Iteration with first row expanded
        await saveScreenshot('03-group-by-iteration-first-row');
    });

    it('switches to Graph view and takes screenshot', async function () {
        this.timeout(30000);
        const driver = browser.driver;

        // Click "Graph" in header bar
        const graphBtn = await webview.findWebElement(
            By.xpath("//div[contains(@class, 'bk-header-right')]//button[contains(@class, 'bk-segmented-btn') and contains(., 'Graph')]")
        );
        await clickWebviewElement(graphBtn);

        // Wait for SVG canvas to render and layout to settle
        await driver.wait(until.elementLocated(By.css('.bk-execution-content-graph svg')), 15000);
        await driver.sleep(2500);

        // Take Screenshot 4: Graph view
        await saveScreenshot('04-execution-graph-view');
    });

    after(async function () {
        if (webview) {
            try {
                await webview.switchBack();
            } catch (ignored) {}
        }
    });
});
