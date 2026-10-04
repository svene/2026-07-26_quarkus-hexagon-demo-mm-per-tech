import { defineConfig } from '@playwright/test';
import * as path from 'path';

export default defineConfig({
  testDir: './tests',
  // Above the 15s expect.poll waits for async (Kafka) inventory updates, well below the 30s default.
  timeout: 20_000,
  globalSetup: require.resolve('./global-setup'),

  // Quarkus dev mode starts the app with Dev Services (Postgres, MongoDB, Kafka via Docker).
  // The build is done in globalSetup; here we only start the server.
  webServer: {
    // -Dquarkus.console.enabled=false prevents the interactive dev console from
    // blocking when Playwright spawns the process without a TTY.
    // -Dquarkus.analytics.disabled=true suppresses the first-run analytics prompt.
    command: 'mvn -pl app-server quarkus:dev -Dnodebug -Dquarkus.console.enabled=false -Dquarkus.analytics.disabled=true'
      // Automatic replenishment and supplier orders would race with the tests' own stock changes (e.g. drain the DC
      // after a restock, or refill it).
      + ' -Dinventory.demand-period=off -Dinventory.auto-replenishment.enabled=false -Dinventory.auto-purchasing.enabled=false'
      // Supplier deliveries follow the order right away, and DC shipments arrive right away, as the tests wait for them.
      + ' -Dsupplier-stub.lead-time=0s -Dcarrier-stub.transit-time=0s',
    url: 'http://localhost:8080/admin',
    timeout: 120_000,
    // Reuse a running server locally so you can keep quarkus:dev open in a terminal (start it with the
    // -D flags above, or automatic replenishment makes the stock assertions flaky).
    // In CI (CI=true) always start fresh.
    reuseExistingServer: !process.env.CI,
    cwd: path.join(__dirname, '..'),
    stdout: 'pipe',
    stderr: 'pipe',
  },

  // One retry absorbs the brief hot-reload window after global-setup rebuilds the JARs.
  retries: 1,

  use: {
    baseURL: 'http://localhost:8080',
    // Default is no limit, so a stuck click/check would otherwise burn the whole test timeout.
    actionTimeout: 3_000,
    screenshot: 'only-on-failure',
  },
});
