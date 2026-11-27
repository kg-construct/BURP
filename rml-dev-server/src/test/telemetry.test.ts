import * as assert from 'assert';
import * as vscode from 'vscode';
import originalPostHog from 'posthog-js';
import {RmlDevClient} from '../client';
import {RmlEvalInlayHintsProvider} from '../evalInlayHintsProvider';
import {TelemetryService, sanitizeStackTrace, sanitizeTelemetryText, telemetry} from '../telemetry';

suite('TelemetryService & Study Pseudo Tests', () => {
    let originalInit: typeof originalPostHog.init;
    let originalCapture: typeof originalPostHog.capture;
    let originalIdentify: typeof originalPostHog.identify;
    let originalOptOutCapturing: typeof originalPostHog.opt_out_capturing;
    let originalOptInCapturing: typeof originalPostHog.opt_in_capturing;

    let initCalls: Array<{token: string; config: Record<string, any> | undefined}>;
    let captureCalls: Array<{event: string; properties: Record<string, any> | undefined}>;
    let identifiedCalls: Array<{distinctId: string; properties: Record<string, any> | undefined}>;
    let optOutCalls: number;
    let optInCalls: number;

    let previousTelemetrySetting: 'unspecified' | 'enabled' | 'disabled' | undefined;
    let createdSubscriptions: vscode.Disposable[][];

    function createMockContext(
        version: string | null = '0.0.4',
        extensionMode: vscode.ExtensionMode = vscode.ExtensionMode.Production
    ): vscode.ExtensionContext {
        const state = new Map<string, unknown>();
        const subscriptions: vscode.Disposable[] = [];
        createdSubscriptions.push(subscriptions);
        return {
            extensionMode,
            subscriptions,
            extension: version !== null ? {packageJSON: {version}} : undefined,
            globalState: {
                get: <T>(key: string) => state.get(key) as T | undefined,
                update: async (key: string, value: unknown) => {
                    state.set(key, value);
                }
            }
        } as unknown as vscode.ExtensionContext;
    }

    setup(async () => {
        initCalls = [];
        captureCalls = [];
        identifiedCalls = [];
        optOutCalls = 0;
        optInCalls = 0;
        createdSubscriptions = [];

        originalInit = originalPostHog.init;
        originalCapture = originalPostHog.capture;
        originalIdentify = originalPostHog.identify;
        originalOptOutCapturing = originalPostHog.opt_out_capturing;
        originalOptInCapturing = originalPostHog.opt_in_capturing;

        originalPostHog.init = ((token: string, config?: Record<string, any>) => {
            initCalls.push({token, config});
        }) as typeof originalPostHog.init;

        originalPostHog.capture = ((event: string, properties?: Record<string, any>) => {
            captureCalls.push({event, properties: properties ?? undefined});
        }) as typeof originalPostHog.capture;

        originalPostHog.identify = ((distinctId: string, properties?: Record<string, any>) => {
            identifiedCalls.push({distinctId, properties});
        }) as typeof originalPostHog.identify;

        originalPostHog.opt_out_capturing = (() => {
            optOutCalls++;
        }) as typeof originalPostHog.opt_out_capturing;

        originalPostHog.opt_in_capturing = (() => {
            optInCalls++;
        }) as typeof originalPostHog.opt_in_capturing;

        const config = vscode.workspace.getConfiguration('rml');
        previousTelemetrySetting = config.get<'unspecified' | 'enabled' | 'disabled'>('telemetry.enabled');
    });

    teardown(async () => {
        for (const subscriptions of createdSubscriptions) {
            for (const sub of subscriptions) {
                sub.dispose();
            }
        }
        originalPostHog.init = originalInit;
        originalPostHog.capture = originalCapture;
        originalPostHog.identify = originalIdentify;
        originalPostHog.opt_out_capturing = originalOptOutCapturing;
        originalPostHog.opt_in_capturing = originalOptInCapturing;
        telemetry.setTestMode(true);

        const config = vscode.workspace.getConfiguration('rml');
        await config.update('telemetry.enabled', previousTelemetrySetting, vscode.ConfigurationTarget.Global);
    });

    test('rml.setStudyPseudo command is registered', async () => {
        const commands = await vscode.commands.getCommands(true);
        assert.ok(commands.includes('rml.setStudyPseudo'), 'Expected rml.setStudyPseudo command to be registered');
    });

    test('setStudyPseudo calls identify with trimmed study_pseudo and machine distinct_id when enabled', async () => {
        const config = vscode.workspace.getConfiguration('rml');
        await config.update('telemetry.enabled', 'enabled', vscode.ConfigurationTarget.Global);

        const service = new TelemetryService();
        service.setTestMode(false);
        service.setStudyPseudo('  P01  ');

        assert.strictEqual(identifiedCalls.length, 1);
        assert.strictEqual(identifiedCalls[0].distinctId, service.getDistinctId());
        assert.deepStrictEqual(identifiedCalls[0].properties, {study_pseudo: 'P01'});
    });

    test('setStudyPseudo ignores empty/whitespace strings and disabled telemetry', async () => {
        const config = vscode.workspace.getConfiguration('rml');
        await config.update('telemetry.enabled', 'enabled', vscode.ConfigurationTarget.Global);

        const service = new TelemetryService();
        service.setTestMode(false);
        service.setStudyPseudo('   ');
        assert.strictEqual(identifiedCalls.length, 0);

        await config.update('telemetry.enabled', 'disabled', vscode.ConfigurationTarget.Global);
        service.setStudyPseudo('P02');
        assert.strictEqual(identifiedCalls.length, 0);
    });

    test('initialize does not call posthog.init when telemetry is disabled', async () => {
        const config = vscode.workspace.getConfiguration('rml');
        await config.update('telemetry.enabled', 'disabled', vscode.ConfigurationTarget.Global);

        const service = new TelemetryService();
        const mockContext = createMockContext('0.0.4');
        service.initialize(mockContext);

        assert.strictEqual(initCalls.length, 0);
        assert.strictEqual(optOutCalls, 0);
        assert.strictEqual(optInCalls, 0);

        service.capture('extension_activated');
        assert.strictEqual(initCalls.length, 0);
        assert.strictEqual(captureCalls.length, 0);
    });

    test('initialize calls posthog.init when enabled and lazily initializes when enabled later before capture or setStudyPseudo', async () => {
        const config = vscode.workspace.getConfiguration('rml');
        await config.update('telemetry.enabled', 'enabled', vscode.ConfigurationTarget.Global);

        const enabledService = new TelemetryService();
        enabledService.initialize(createMockContext('0.0.4'));
        assert.strictEqual(initCalls.length, 1);
        assert.strictEqual(optInCalls, 1);

        // Runtime opt-out and re-opt-in via configuration change
        await config.update('telemetry.enabled', 'disabled', vscode.ConfigurationTarget.Global);
        assert.strictEqual(optOutCalls, 1);
        assert.strictEqual(initCalls.length, 1);

        await config.update('telemetry.enabled', 'enabled', vscode.ConfigurationTarget.Global);
        assert.strictEqual(optInCalls, 2);
        assert.strictEqual(initCalls.length, 1);

        // Lazy init on capture() when telemetry was disabled/test-gated at initialize()
        initCalls = [];
        captureCalls = [];
        const lazyCaptureService = new TelemetryService();
        lazyCaptureService.initialize(createMockContext('1.2.3', vscode.ExtensionMode.Test));
        assert.strictEqual(initCalls.length, 0);

        lazyCaptureService.setTestMode(false);
        lazyCaptureService.capture('extension_activated');
        assert.strictEqual(initCalls.length, 1);
        assert.strictEqual(captureCalls.length, 1);

        // Lazy init on setStudyPseudo() when telemetry was disabled/test-gated at initialize()
        initCalls = [];
        identifiedCalls = [];
        const lazyPseudoService = new TelemetryService();
        lazyPseudoService.initialize(createMockContext('1.2.3', vscode.ExtensionMode.Test));
        assert.strictEqual(initCalls.length, 0);

        lazyPseudoService.setTestMode(false);
        lazyPseudoService.setStudyPseudo('P42');
        assert.strictEqual(initCalls.length, 1);
        assert.strictEqual(identifiedCalls.length, 1);
        assert.deepStrictEqual(identifiedCalls[0].properties, {study_pseudo: 'P42'});
    });

    test('capture sends $app_version from context.extension.packageJSON.version', async () => {
        const config = vscode.workspace.getConfiguration('rml');
        await config.update('telemetry.enabled', 'enabled', vscode.ConfigurationTarget.Global);

        const service = new TelemetryService();
        service.initialize(createMockContext('2.5.9'));
        service.capture('extension_activated');

        assert.strictEqual(captureCalls.length, 1);
        assert.strictEqual(captureCalls[0].properties?.$app_version, '2.5.9');
        assert.notStrictEqual(captureCalls[0].properties?.$app_version, '0.0.1');

        // Fallback when packageJSON is absent
        captureCalls = [];
        const unversionedService = new TelemetryService();
        unversionedService.initialize(createMockContext(null));
        unversionedService.capture('extension_activated');

        assert.strictEqual(captureCalls.length, 1);
        assert.strictEqual(captureCalls[0].properties?.$app_version, 'unknown');
    });

    test('captureException redacts URIs, paths, emails, and quoted literals while preserving stack basenames and line:col', async () => {
        const config = vscode.workspace.getConfiguration('rml');
        await config.update('telemetry.enabled', 'enabled', vscode.ConfigurationTarget.Global);

        const service = new TelemetryService();
        service.initialize(createMockContext('0.0.4'));

        const err = new Error(
            'Failed reading "Alice Secret Value" at /home/alice/work/mapping.ttl, C:\\Users\\alice\\data.csv, file:///Users/alice/test.ttl, https://secret.internal/api?k=1, jdbc:postgresql://db.local:5432/prod for alice@example.com'
        );
        err.name = 'MappingLoadError';
        const stackLines = [
            'MappingLoadError: Failed on /home/alice/secret.ttl for "Private Token"',
            '    at RmlClient.load (/home/alice/Documents/Dev/BURP/rml-dev-server/out/client.js:128:19)',
            '    at async runMapping (C:\\Users\\alice\\AppData\\extension.js:44:7)',
            '    at file:///Users/alice/ext/out/commands.js:90:12',
            '    at /var/folders/tmp/runner.js:12:3'
        ];
        for (let i = 0; i < 25; i++) {
            stackLines.push(`    at frame${i} (/home/alice/src/file${i}.ts:${i + 1}:5)`);
        }
        err.stack = stackLines.join('\n');

        service.captureException(err, 'Loading /workspace/my-project/mapping.rml for "Customer Corp"');

        assert.strictEqual(captureCalls.length, 1);
        const props = captureCalls[0].properties!;
        assert.strictEqual(captureCalls[0].event, '$exception');
        assert.strictEqual(props.$exception_type, 'MappingLoadError');

        const msg = props.$exception_message as string;
        assert.ok(msg.length <= 256, `Expected sanitized message length <= 256, got ${msg.length}`);
        assert.ok(!msg.includes('alice'), `Leaked username in message: ${msg}`);
        assert.ok(!msg.includes('Alice Secret Value'), `Leaked quoted literal in message: ${msg}`);
        assert.ok(!msg.includes('secret.internal'), `Leaked URL in message: ${msg}`);
        assert.ok(!msg.includes('db.local'), `Leaked JDBC URI in message: ${msg}`);

        const directSanitized = sanitizeTelemetryText(
            'Uri file:///home/alice/a.ttl https://a.b/c jdbc:sqlite:/tmp/db Email bob@example.org Paths /Users/bob/x.ttl /workspace/proj/y.ttl C:\\Users\\bob\\z.ttl Quotes "long secret" \'abcd\' \'ok\''
        );
        assert.ok(directSanitized.includes('<redacted-uri>'));
        assert.ok(directSanitized.includes('<redacted-email>'));
        assert.ok(directSanitized.includes('<redacted-path>'));
        assert.ok(directSanitized.includes('"<redacted>"'));
        assert.ok(directSanitized.includes("'ok'"), `Expected short quote to be preserved: ${directSanitized}`);
        assert.ok(!directSanitized.includes('bob'));
        assert.ok(!directSanitized.includes('long secret'));
        assert.ok(!directSanitized.includes('abcd'));

        const overlong = sanitizeTelemetryText('a'.repeat(300));
        assert.ok(overlong.length <= 256, `Expected truncated length <= 256, got ${overlong.length}`);

        const stack = props.$exception_stack_trace_raw as string;
        const resultLines = stack.split('\n');
        assert.strictEqual(resultLines.length, 20);
        assert.ok(!stack.includes('alice'), `Leaked username in stack: ${stack}`);
        assert.ok(!stack.includes('Private Token'), `Leaked quoted token in stack: ${stack}`);
        assert.ok(resultLines[1].includes('RmlClient.load (client.js:128:19)'), `Unexpected frame 1: ${resultLines[1]}`);
        assert.ok(resultLines[2].includes('async runMapping (extension.js:44:7)'), `Unexpected frame 2: ${resultLines[2]}`);
        assert.ok(resultLines[3].includes('commands.js:90:12'), `Unexpected frame 3: ${resultLines[3]}`);
        assert.ok(resultLines[4].includes('runner.js:12:3'), `Unexpected frame 4: ${resultLines[4]}`);

        const sanitizedContext = props.context as string;
        assert.ok(!sanitizedContext.includes('my-project'));
        assert.ok(!sanitizedContext.includes('Customer Corp'));
        assert.ok(sanitizedContext.includes('<redacted-path>'));
        assert.ok(sanitizedContext.includes('"<redacted>"'));
        assert.strictEqual(sanitizeStackTrace(undefined), undefined);
    });

    test('RmlEvalInlayHintsProvider.resolveInlayHint captures inline_evaluate_hover with hint_kind and without hint_label', async () => {
        const config = vscode.workspace.getConfiguration('rml');
        await config.update('telemetry.enabled', 'enabled', vscode.ConfigurationTarget.Global);

        const provider = new RmlEvalInlayHintsProvider({} as RmlDevClient);
        const tokenSource = new vscode.CancellationTokenSource();
        const hint = new vscode.InlayHint(
            new vscode.Position(0, 0),
            '"sensitive user value"',
            vscode.InlayHintKind.Type
        );

        try {
            telemetry.setTestMode(false);
            captureCalls = [];
            await provider.resolveInlayHint(hint, tokenSource.token);
        } finally {
            telemetry.setTestMode(true);
            tokenSource.dispose();
        }

        assert.strictEqual(captureCalls.length, 1);
        assert.strictEqual(captureCalls[0].event, 'inline_evaluate_hover');
        const props = captureCalls[0].properties!;
        assert.strictEqual(props.hint_kind, 'type');
        assert.strictEqual('hint_label' in props, false);
        assert.ok(!JSON.stringify(props).includes('sensitive user value'));
    });
});
