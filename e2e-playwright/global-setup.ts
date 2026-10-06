import type { FullConfig } from '@playwright/test';

// The build runs in the webServer command, before the dev server starts, so a fresh server needs no warm-up. A reused
// dev server (reuseExistingServer) recompiles changed sources itself, but only live-reloads (~13 s) on the next HTTP
// request; without this warm-up the first tests would send that request and fail with ERR_EMPTY_RESPONSE.
const WARM_UP_TIMEOUT_MS = 90_000;
// Quarkus scans for changes at most every 2 s (HOT_REPLACEMENT_INTERVAL in VertxHttpHotReplacementSetup), so OKs within
// a shorter window may all come before the scan that triggers the reload.
const OK_STREAK_MS = 2_500;
const POLL_INTERVAL_MS = 250;

export default async function globalSetup(config: FullConfig) {
  await warmUp(new URL('/admin', config.projects[0].use.baseURL).toString());
}

// Triggers a pending live reload and waits until the app has answered every request for OK_STREAK_MS.
async function warmUp(url: string) {
  console.log(`Warming up ${url} (absorbs a reused dev server's live reload)...`);
  const deadline = Date.now() + WARM_UP_TIMEOUT_MS;
  let streakStart: number | undefined;
  while (streakStart === undefined || Date.now() - streakStart < OK_STREAK_MS) {
    if (Date.now() > deadline) throw new Error(`${url} did not answer OK for ${OK_STREAK_MS} ms within ${WARM_UP_TIMEOUT_MS} ms`);
    let ok = false;
    try {
      ok = (await fetch(url)).ok;
    } catch {
      // Connection dropped by the reload: the streak starts over.
    }
    streakStart = ok ? streakStart ?? Date.now() : undefined;
    await new Promise(resolve => setTimeout(resolve, POLL_INTERVAL_MS));
  }
  console.log('Dev server is warm.\n');
}
