import * as assert from 'assert';
import {setupTestServer, teardownTestServer, TestContext} from './testHelper';

suite('RML Dev Server Communication & Health Check Test', function () {
    this.timeout(30000);

    let ctx: TestContext;

    setup(async () => {
        ctx = await setupTestServer('Health Check Test Output');
    });

    teardown(async () => {
        await teardownTestServer(ctx);
    });

    test('Server process starts and responds to health check', async function () {
        assert.ok(ctx.port > 0, `Expected valid port number, got ${ctx.port}`);
        assert.strictEqual(ctx.client.isConnected(), true, 'Expected client to be connected');

        const health = await ctx.client.healthCheck(ctx.port);
        assert.strictEqual(health.ok, true, `Health check failed: ${health.message}`);
        assert.strictEqual(health.serverPort, ctx.port);
        assert.ok(health.latencyMs !== undefined && health.latencyMs >= 0);
    });
});
