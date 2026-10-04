import { test, expect, Page, APIRequestContext } from '@playwright/test';

// Unique suffix prevents cross-run state collisions in the persistent Postgres inventory.
const RUN_ID = Date.now();

// Unique test products are ordered via the JSON API; the delivery reaches the DC asynchronously via Kafka.
async function stockDc(request: APIRequestContext, productName: string, quantity: number) {
  const res = await request.post('/api/products/order-fruits', { data: { productName, quantity } });
  expect(res.ok()).toBeTruthy();
}

// /locations shows every store and the online FC; each helper is scoped to one location's section.
function stockRow(page: Page, locationId: string, productName: string) {
  return page.locator(`#stock-${locationId} tbody tr`).filter({ has: page.getByRole('cell', { name: productName, exact: true }) });
}

function requestRow(page: Page, locationId: string, productName: string) {
  return page.locator(`#requests-${locationId} tbody tr`).filter({ hasText: productName });
}

// Columns of the stock table: Name, Type, Available, In transit, Avg, Min, Max, DC, ...
const AVAILABLE = 2, IN_TRANSIT = 3, AVG = 4, MIN = 5, MAX = 6, DC = 7;
const cell = (page: Page, locationId: string, productName: string, column: number) =>
  stockRow(page, locationId, productName).getByRole('cell').nth(column);

test('the landing page links admin, locations and shop, none of which shows a nav', async ({ page }) => {
  for (const [link, heading] of [['Admin', 'Supermarket – Admin'], ['Locations', 'Supermarket – Locations'], ['Shop', 'Supermarket – Shop']]) {
    await page.goto('/');
    await page.locator('#entry-points').getByRole('link', { name: link, exact: true }).click();
    await expect(page.getByRole('heading', { name: heading })).toBeVisible();
    await expect(page.locator('#location-nav')).toHaveCount(0);
  }
});

test('the locations page shows every store and the online FC as sections, in order', async ({ page }) => {
  await page.goto('/locations');
  await expect(page.locator('#app section h2')).toHaveText(['Store Zurich', 'Store Bern', 'Store Basel', 'Online FC']);
});

test('a store lists what the DC carries and gets a request served live, without a reload', async ({ page }) => {
  const name = `Quince-${RUN_ID}`;
  await page.goto('/locations');
  await stockDc(page.request, name, 20);

  // The DC's delivery appears through the SSE-triggered refresh: 0 here, 20 at the DC.
  await expect(stockRow(page, 'zurich', name)).toBeVisible({ timeout: 15_000 });
  await expect(cell(page, 'zurich', name, AVAILABLE)).toHaveText('0');
  await expect(cell(page, 'zurich', name, DC)).toHaveText('20');
  // No stock row here yet, so nothing learned yet either.
  await expect(cell(page, 'zurich', name, MIN)).toHaveText('–');

  await stockRow(page, 'zurich', name).getByRole('button', { name: '10', exact: true }).click();

  // Shipped by the DC; with the e2e server's 0 s transit time it arrives right after (via Kafka), so nothing stays in transit.
  await expect(cell(page, 'zurich', name, AVAILABLE)).toHaveText('10');
  await expect(cell(page, 'zurich', name, IN_TRANSIT)).toHaveText('0');
  await expect(cell(page, 'zurich', name, DC)).toHaveText('10');
  const requested = requestRow(page, 'zurich', name);
  await expect(requested).toContainText('10 / 10');
  await expect(requested).toContainText('FULFILLED');
  await expect(requested).toContainText('manual');

  // The first shipment created the stock row with a store's cold-start levels (read-only); 10 is below min, so red.
  await expect(cell(page, 'zurich', name, AVG)).toHaveText('10.0');
  await expect(cell(page, 'zurich', name, MIN)).toHaveText('17');
  await expect(cell(page, 'zurich', name, MAX)).toHaveText('47');
  await expect(cell(page, 'zurich', name, AVAILABLE)).toHaveClass(/has-text-danger/);
});

test('a request the DC cannot fully serve stays pending until head office fulfils or rejects it', async ({ page, context }) => {
  const name = `Guava-${RUN_ID}`;
  await stockDc(page.request, name, 3);
  await page.goto('/locations');
  await expect(stockRow(page, 'basel', name)).toBeVisible({ timeout: 15_000 });

  await stockRow(page, 'basel', name).getByRole('button', { name: '10', exact: true }).click();
  await expect(cell(page, 'basel', name, AVAILABLE)).toHaveText('3');
  const requested = requestRow(page, 'basel', name);
  await expect(requested).toContainText('3 / 10');
  await expect(requested).toContainText('PENDING');

  const admin = await context.newPage();
  await admin.goto('/admin');
  const pending = admin.locator('#pending-requests tbody tr').filter({ hasText: name });
  await expect(pending).toContainText('Store Basel');
  await pending.getByRole('button', { name: 'Reject' }).click();
  await expect(pending).toHaveCount(0);

  // The locations page follows without a reload.
  await expect(requested).toContainText('REJECTED');
  await expect(cell(page, 'basel', name, AVAILABLE)).toHaveText('3');
});
