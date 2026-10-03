import { test, expect, Page, APIRequestContext } from '@playwright/test';

// Unique suffix prevents cross-run state collisions in the persistent Postgres inventory.
const RUN_ID = Date.now();

// Unique test products are ordered via the JSON API; the delivery reaches the DC asynchronously via Kafka.
async function stockDc(request: APIRequestContext, productName: string, quantity: number) {
  const res = await request.post('/api/products/order-fruits', { data: { productName, quantity } });
  expect(res.ok()).toBeTruthy();
}

function stockRow(page: Page, productName: string) {
  return page.locator('#location-stock tbody tr').filter({ has: page.getByRole('cell', { name: productName, exact: true }) });
}

// Name, Type, Available, DC, ...
const cell = (page: Page, productName: string, column: number) => stockRow(page, productName).getByRole('cell').nth(column);

test('the nav switches between the locations and marks the current one', async ({ page }) => {
  await page.goto('/admin');
  await page.locator('#location-nav').getByRole('link', { name: 'Store Bern' }).click();
  await expect(page).toHaveURL('/locations/bern');
  await expect(page.getByRole('heading', { name: 'Supermarket – Store Bern' })).toBeVisible();
  await expect(page.locator('#location-nav li.is-active')).toHaveText('Store Bern');

  await page.locator('#location-nav').getByRole('link', { name: 'Online FC' }).click();
  await expect(page).toHaveURL('/locations/online');
  await expect(page.locator('#location-nav li.is-active')).toHaveText('Online FC');
});

test('a store lists what the DC carries and gets a request served live, without a reload', async ({ page }) => {
  const name = `Quince-${RUN_ID}`;
  await page.goto('/locations/zurich');
  await stockDc(page.request, name, 10);

  // The DC's delivery appears through the SSE-triggered refresh: 0 here, 10 at the DC.
  await expect(stockRow(page, name)).toBeVisible({ timeout: 15_000 });
  await expect(cell(page, name, 2)).toHaveText('0');
  await expect(cell(page, name, 3)).toHaveText('10');

  const request = stockRow(page, name).getByRole('button', { name: 'Request' });
  await expect(request).toBeDisabled();
  await stockRow(page, name).locator('input[name="quantity"]').fill('4');
  await request.click();

  await expect(cell(page, name, 2)).toHaveText('4');
  await expect(cell(page, name, 3)).toHaveText('6');
  const requestRow = page.locator('#location-requests tbody tr').filter({ hasText: name });
  await expect(requestRow).toContainText('4 / 4');
  await expect(requestRow).toContainText('FULFILLED');
});

test('a request the DC cannot fully serve stays pending until head office fulfils or rejects it', async ({ page, context }) => {
  const name = `Guava-${RUN_ID}`;
  await stockDc(page.request, name, 3);
  await page.goto('/locations/basel');
  await expect(stockRow(page, name)).toBeVisible({ timeout: 15_000 });

  await stockRow(page, name).locator('input[name="quantity"]').fill('5');
  await stockRow(page, name).getByRole('button', { name: 'Request' }).click();
  await expect(cell(page, name, 2)).toHaveText('3');
  const requestRow = page.locator('#location-requests tbody tr').filter({ hasText: name });
  await expect(requestRow).toContainText('3 / 5');
  await expect(requestRow).toContainText('PENDING');

  const admin = await context.newPage();
  await admin.goto('/admin');
  const pending = admin.locator('#pending-requests tbody tr').filter({ hasText: name });
  await expect(pending).toContainText('Store Basel');
  await pending.getByRole('button', { name: 'Reject' }).click();
  await expect(pending).toHaveCount(0);

  // The store's page follows without a reload.
  await expect(requestRow).toContainText('REJECTED');
  await expect(cell(page, name, 2)).toHaveText('3');
});
