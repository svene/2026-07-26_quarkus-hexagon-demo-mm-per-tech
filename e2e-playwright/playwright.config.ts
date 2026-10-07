import { defineConfig } from '@playwright/test';
import * as path from 'path';

export default defineConfig({
  testDir: './tests',
  // Above the 15s expect.poll waits for async (Kafka) inventory updates, well below the 30s default.
  timeout: 20_000,
  globalSetup: require.resolve('./global-setup'),

  // Quarkus dev mode starts the app with Dev Services (Postgres, MongoDB, Kafka via Docker).
  webServer: {
    // The build runs first, so the dev server starts on the finished classes. Building after the server is up (as
    // global-setup.ts used to) replaced the classes under it and forced a live reload, and two overlapping reloads broke
    // the app (ClassCastException, mass timeouts).
    // -Dquarkus.console.enabled=false prevents the interactive dev console from
    // blocking when Playwright spawns the process without a TTY.
    // -Dquarkus.analytics.disabled=true suppresses the first-run analytics prompt.
    command: 'mvn install -DskipTests -q'
      + ' && mvn -pl app-server quarkus:dev -Dnodebug -Dquarkus.console.enabled=false -Dquarkus.analytics.disabled=true'
      // Automatic replenishment and supplier orders would race with the tests' own stock changes (e.g. drain the DC
      // after a restock, or refill it).
      + ' -Dinventory.demand-period=off -Dinventory.auto-replenishment.enabled=false -Dinventory.auto-purchasing.enabled=false'
      // No automatic tills, like the other automatic steps (with the cashpoint stub off no store reports anyway).
      + ' -Dinventory.auto-tills.enabled=false'
      // The DC is not seeded: the tests expect an empty inventory after their reset.
      + ' -Dinventory.dc-seed.enabled=false'
      // Supplier deliveries follow the order right away, and DC shipments arrive right away, as the tests wait for them.
      + ' -Dsupplier-stub.lead-time=0s -Dcarrier-stub.transit-time=0s'
      // No simulated store customers: only the tests move stock.
      + ' -Dcashpoint-stub.tick=off',
    url: 'http://localhost:8080/admin',
    // Build + dev server start.
    timeout: 240_000,
    // Reuse a running server locally so you can keep quarkus:dev open in a terminal (start it with the
    // -D flags above, or automatic replenishment makes the stock assertions flaky). No build runs then: the dev
    // server recompiles changed modules itself on the next request, and global-setup.ts absorbs that live reload.
    // In CI (CI=true) always start fresh.
    reuseExistingServer: !process.env.CI,
    cwd: path.join(__dirname, '..'),
    stdout: 'pipe',
    stderr: 'pipe',
  },

  // No retries: with the build done before the server starts there is no reload window left to absorb, and a retry
  // would hide flaky tests.
  retries: 0,

  use: {
    baseURL: 'http://localhost:8080',
    // Default is no limit, so a stuck click/check would otherwise burn the whole test timeout.
    actionTimeout: 3_000,
    screenshot: 'only-on-failure',
  },
});
