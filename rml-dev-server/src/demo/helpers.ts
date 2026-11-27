import * as fs from 'fs';
import * as path from 'path';
import {By, EditorView, until, VSBrowser, WebDriver, WebElement} from 'vscode-extension-tester';

export interface DemoContext {
    browser: VSBrowser;
    driver: WebDriver;
    demosDir: string;
    mappingPath: string;
    originalContent: string;
}

export interface PrepareOptions {
    windowWidth?: number;
    windowHeight?: number;
}

const CURSOR_INIT_SCRIPT = `
(() => {
    if (document.getElementById('demo-virtual-cursor')) return;
    const cursor = document.createElement('div');
    cursor.id = 'demo-virtual-cursor';
    cursor.style.cssText = [
        'position: fixed',
        'left: 0px',
        'top: 0px',
        'width: 22px',
        'height: 22px',
        'pointer-events: none',
        'z-index: 2147483647',
        'transition: transform 0.28s cubic-bezier(0.22, 1, 0.36, 1), opacity 0.15s ease',
        'opacity: 0',
        'filter: drop-shadow(0 2px 4px rgba(0,0,0,0.45))'
    ].join(';');

    const svgNS = 'http://www.w3.org/2000/svg';
    const svg = document.createElementNS(svgNS, 'svg');
    svg.setAttribute('width', '22');
    svg.setAttribute('height', '22');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('fill', 'none');

    const pathEl = document.createElementNS(svgNS, 'path');
    pathEl.setAttribute('d', 'M4 2L20 11L12.5 13.5L10 21L4 2Z');
    pathEl.setAttribute('fill', '#ffffff');
    pathEl.setAttribute('stroke', '#111111');
    pathEl.setAttribute('stroke-width', '2');
    pathEl.setAttribute('stroke-linejoin', 'round');
    svg.appendChild(pathEl);

    const ripple = document.createElement('div');
    ripple.id = 'demo-virtual-ripple';
    ripple.style.cssText = [
        'position: absolute',
        'left: -8px',
        'top: -8px',
        'width: 22px',
        'height: 22px',
        'border-radius: 50%',
        'border: 2px solid #007acc',
        'opacity: 0',
        'transform: scale(0.5)',
        'transition: transform 0.25s ease-out, opacity 0.25s ease-out'
    ].join(';');

    cursor.appendChild(svg);
    cursor.appendChild(ripple);
    document.body.appendChild(cursor);
})();
`;

export async function hideCursor(driver: WebDriver): Promise<void> {
    try {
        await driver.executeScript(`
            const c = document.getElementById('demo-virtual-cursor');
            if (c) {
                c.style.opacity = '0';
                delete c.dataset.x;
                delete c.dataset.y;
            }
        `);
    } catch {
        // Ignore if page is not ready
    }
}

export async function moveCursorToPoint(driver: WebDriver, targetX: number, targetY: number): Promise<void> {
    await driver.executeScript(CURSOR_INIT_SCRIPT);
    await driver.executeAsyncScript(
        `
        const targetX = arguments[0];
        const targetY = arguments[1];
        const done = arguments[arguments.length - 1];
        const c = document.getElementById('demo-virtual-cursor');
        if (!c) { done(); return; }

        c.style.transition = 'none';
        const isHidden = c.style.opacity === '0' || !c.dataset.x;
        const startX = isHidden ? Math.round(window.innerWidth / 2) : parseFloat(c.dataset.x);
        const startY = isHidden ? Math.round(window.innerHeight / 2) : parseFloat(c.dataset.y);

        c.style.opacity = '1';
        c.style.transform = 'translate(' + startX + 'px, ' + startY + 'px)';

        // Quadratic Bezier control point with a slight natural curve
        const dx = targetX - startX;
        const dy = targetY - startY;
        const ctrlX = (startX + targetX) / 2 - dy * 0.18;
        const ctrlY = (startY + targetY) / 2 + dx * 0.18;

        const duration = 380;
        const startTime = performance.now();
        function step(now) {
            const t = Math.min(1, (now - startTime) / duration);
            const u = 1 - Math.pow(1 - t, 3);
            const inv = 1 - u;
            const x = inv * inv * startX + 2 * inv * u * ctrlX + u * u * targetX;
            const y = inv * inv * startY + 2 * inv * u * ctrlY + u * u * targetY;
            c.style.transform = 'translate(' + Math.round(x) + 'px, ' + Math.round(y) + 'px)';
            if (t < 1) {
                requestAnimationFrame(step);
            } else {
                c.dataset.x = String(targetX);
                c.dataset.y = String(targetY);
                done();
            }
        }
        requestAnimationFrame(step);
        `,
        Math.round(targetX),
        Math.round(targetY)
    );
}

export async function moveCursorTo(driver: WebDriver, element: WebElement): Promise<void> {
    const rect = await element.getRect();
    await moveCursorToPoint(driver, rect.x + rect.width / 2, rect.y + rect.height / 2);
}

export async function clickWithCursor(driver: WebDriver, element: WebElement): Promise<void> {
    await moveCursorTo(driver, element);
    await driver.sleep(350);
    await driver.executeScript(`
        const r = document.getElementById('demo-virtual-cursor')?.querySelector('#demo-virtual-ripple');
        if (r) {
            r.style.transition = 'none';
            r.style.opacity = '0.9';
            r.style.transform = 'scale(0.5)';
            requestAnimationFrame(() => {
                r.style.transition = 'transform 0.28s ease-out, opacity 0.28s ease-out';
                r.style.opacity = '0';
                r.style.transform = 'scale(1.8)';
            });
        }
    `);
    await element.click();
    await driver.sleep(220);
}

export async function closeMatchingEditors(predicate: (title: string) => boolean): Promise<void> {
    const editorView = new EditorView();
    const groups = await editorView.getEditorGroups();
    for (const group of groups) {
        const titles = await group.getOpenEditorTitles();
        for (const title of titles) {
            if (predicate(title)) {
                await group.closeEditor(title);
            }
        }
    }
}

export async function closeSidebarIfOpen(driver: WebDriver): Promise<void> {
    try {
        const checkedItems = await driver.findElements(
            By.css('.activitybar .action-item.checked a.action-label')
        );
        if (checkedItems.length > 0) {
            await checkedItems[0].click();
            await driver.sleep(300);
        }
    } catch {
        // Ignore if sidebar is already closed
    }
}

export async function prepareDemoEnvironment(
    mappingPathOrRel: string,
    options: PrepareOptions = {}
): Promise<DemoContext> {
    const demosDir = path.resolve(__dirname, '../../demos');
    const mappingPath = path.isAbsolute(mappingPathOrRel)
        ? mappingPathOrRel
        : path.resolve(__dirname, '../../../src/test/resources/burp', mappingPathOrRel);
    const originalContent = fs.readFileSync(mappingPath, 'utf8');

    const browser = VSBrowser.instance;
    await browser.waitForWorkbench();

    await hideCursor(browser.driver);
    await closeSidebarIfOpen(browser.driver);

    const editorView = new EditorView();
    try {
        await editorView.closeAllEditors();
    } catch {
        // Ignore if no editors are open
    }

    await browser.openResources(mappingPath);

    // Wait for BURP Dev Server status bar to indicate connection & warm-up
    await browser.driver.wait(
        until.elementLocated(By.xpath("//*[contains(@aria-label, 'RML: Connected') or contains(., 'RML: Connected')]")),
        30000
    );
    await browser.driver.sleep(1500);

    const width = options.windowWidth ?? 1120;
    const height = options.windowHeight ?? 720;
    try {
        await browser.driver.executeScript(`window.resizeTo(${width}, ${height});`);
        await browser.driver.sleep(300);
    } catch {
        // Ignore if window resizing is restricted
    }

    return {
        browser,
        driver: browser.driver,
        demosDir,
        mappingPath,
        originalContent
    };
}

export function restoreMappingFile(ctx: DemoContext): void {
    if (ctx?.mappingPath && ctx?.originalContent !== undefined) {
        fs.writeFileSync(ctx.mappingPath, ctx.originalContent, 'utf8');
    }
}

export async function focusToken(driver: WebDriver, tokenText: string): Promise<WebElement> {
    const tokenEl = await driver.wait(
        until.elementLocated(
            By.xpath(
                `//div[contains(@class, 'view-line') and not(contains(@class, 'view-lines'))]//span[not(*) and contains(., '${tokenText}')]`
            )
        ),
        10000
    );
    await tokenEl.click();
    await driver.sleep(200);
    const inputs = await driver.findElements(By.css('.monaco-editor .native-edit-context, .monaco-editor textarea.inputarea'));
    if (inputs.length > 0) {
        return inputs[0];
    }
    return driver.switchTo().activeElement();
}

export async function focusTokenOnLine(
    driver: WebDriver,
    lineSubstring: string,
    tokenText: string
): Promise<WebElement> {
    const tokenEl = await driver.wait(
        until.elementLocated(
            By.xpath(
                `//div[contains(@class, 'view-line') and not(contains(@class, 'view-lines')) and contains(., '${lineSubstring}')]//span[not(*) and contains(., '${tokenText}')]`
            )
        ),
        10000
    );
    await tokenEl.click();
    await driver.sleep(200);
    const inputs = await driver.findElements(By.css('.monaco-editor .native-edit-context, .monaco-editor textarea.inputarea'));
    if (inputs.length > 0) {
        return inputs[0];
    }
    return driver.switchTo().activeElement();
}

export async function clickEditorTitleAction(driver: WebDriver, labelSubstring: string): Promise<WebElement> {
    const btn = await driver.wait(
        until.elementLocated(
            By.xpath(`//a[contains(@aria-label, '${labelSubstring}') or contains(@title, '${labelSubstring}')]`)
        ),
        15000
    );
    await clickWithCursor(driver, btn);
    return btn;
}
