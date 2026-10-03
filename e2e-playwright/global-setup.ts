import { execSync } from 'child_process';
import * as path from 'path';
import type { FullConfig } from '@playwright/test';

// Playwright starts the webServer (quarkus:dev) *before* global setup. The rebuild below replaces the jars under the
// running dev server, which only notices that on the next HTTP request and then live-reloads (~10 s). Without this
// warm-up, the first tests would send that request and fail with ERR_EMPTY_RESPONSE.
const WARM_UP_TIMEOUT_MS = 90_000;
const SUCCESSES_IN_A_ROW = 3;

export default async function globalSetup(config: FullConfig) {
  const rootDir = path.resolve(__dirname, '..');
  console.log('\nBuilding Maven project (skipping tests)...');
  execSync('mvn install -DskipTests -q', { cwd: rootDir, stdio: 'inherit' });
  console.log('Maven build complete.\n');

  await warmUp(new URL('/admin', config.projects[0].use.baseURL).toString());
}

// Triggers the live reload and waits until the app has answered several requests in a row.
async function warmUp(url: string) {
  console.log(`Warming up ${url} (absorbs the dev server's live reload)...`);
  const deadline = Date.now() + WARM_UP_TIMEOUT_MS;
  let successes = 0;
  while (successes < SUCCESSES_IN_A_ROW) {
    if (Date.now() > deadline) throw new Error(`${url} did not answer ${SUCCESSES_IN_A_ROW}× in a row within ${WARM_UP_TIMEOUT_MS} ms`);
    try {
      successes = (await fetch(url)).ok ? successes + 1 : 0;
    } catch {
      successes = 0;
    }
    await new Promise(resolve => setTimeout(resolve, 1_000));
  }
  console.log('Dev server is warm.\n');
}
