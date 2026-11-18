import * as assert from 'assert';
import * as vscode from 'vscode';
import originalPostHog from 'posthog-js';
import {TelemetryService} from '../telemetry';

suite('TelemetryService & Study Pseudo Tests', () => {
    let originalIdentify: typeof originalPostHog.identify;
    let identifiedCalls: Array<{distinctId: string; properties: Record<string, any> | undefined}>;
    let previousTelemetrySetting: 'unspecified' | 'enabled' | 'disabled' | undefined;

    setup(async () => {
        identifiedCalls = [];
        originalIdentify = originalPostHog.identify;
        originalPostHog.identify = ((distinctId: string, properties?: Record<string, any>) => {
            identifiedCalls.push({distinctId, properties});
        }) as typeof originalPostHog.identify;

        const config = vscode.workspace.getConfiguration('rml');
        previousTelemetrySetting = config.get<'unspecified' | 'enabled' | 'disabled'>('telemetry.enabled');
    });

    teardown(async () => {
        originalPostHog.identify = originalIdentify;
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
});
