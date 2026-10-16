import * as esbuild from 'esbuild';

const production = process.argv.includes('--production');
const watch = process.argv.includes('--watch');

/**
 * @type {import('esbuild').Plugin}
 */
const esbuildProblemMatcherPlugin = {
    name: 'esbuild-problem-matcher',

    setup(build) {
        build.onStart(() => {
            console.log('[watch] build started');
        });
        build.onEnd((result) => {
            result.errors.forEach(({text, location}) => {
                console.error(`✘ [ERROR] ${text}`);
                if (location == null) return;
                console.error(`    ${location.file}:${location.line}:${location.column}:`);
            });
            console.log('[watch] build finished');
        });
    },
};

/** @type {import('esbuild').BuildOptions} */
const extensionConfig = {
    entryPoints: ['src/extension.ts'],
    bundle: true,
    outfile: 'dist/extension.js',
    external: ['vscode'],
    format: 'cjs',
    platform: 'node',
    target: 'node20',
    sourcemap: !production,
    sourcesContent: false,
    minify: production,
    logLevel: 'warning',
    plugins: [esbuildProblemMatcherPlugin],
};

/** @type {import('esbuild').BuildOptions} */
const webviewConfig = {
    entryPoints: ['src/webview/index.tsx'],
    bundle: true,
    outfile: 'dist/webview.js',
    format: 'iife',
    loader: {
        '.ttf': 'dataurl',
    },
    sourcemap: !production,
    sourcesContent: false,
    minify: production,
    logLevel: 'warning',
    plugins: [esbuildProblemMatcherPlugin],
};

async function main() {
    const extCtx = await esbuild.context(extensionConfig);
    const webviewCtx = await esbuild.context(webviewConfig);

    if (watch) {
        await Promise.all([extCtx.watch(), webviewCtx.watch()]);
    } else {
        await Promise.all([extCtx.rebuild(), webviewCtx.rebuild()]);
        await Promise.all([extCtx.dispose(), webviewCtx.dispose()]);
        console.log(`Build complete (${production ? 'production' : 'development'}).`);
    }
}

main().catch((err) => {
    console.error(err);
    process.exit(1);
});
