import { defineConfig } from '@playwright/test';
import * as path from 'path';

export default defineConfig({
  testDir: './tests',
  globalSetup: require.resolve('./global-setup'),

  // Quarkus dev mode starts the app with Dev Services (Postgres, MongoDB, Kafka via Docker).
  // The build is done in globalSetup; here we only start the server.
  webServer: {
    // -Dquarkus.console.enabled=false prevents the interactive dev console from
    // blocking when Playwright spawns the process without a TTY.
    // -Dquarkus.analytics.disabled=true suppresses the first-run analytics prompt.
    command: 'mvn -pl app-server quarkus:dev -Dnodebug -Dquarkus.console.enabled=false -Dquarkus.analytics.disabled=true',
    url: 'http://localhost:8080/products',
    timeout: 300_000,
    // Reuse a running server locally so you can keep quarkus:dev open in a terminal.
    // In CI (CI=true) always start fresh.
    reuseExistingServer: !process.env.CI,
    cwd: path.join(__dirname, '..'),
    stdout: 'pipe',
    stderr: 'pipe',
  },

  use: {
    baseURL: 'http://localhost:8080',
    screenshot: 'only-on-failure',
  },
});
