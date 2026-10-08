import { test, expect } from '@playwright/test';

// Unique suffix prevents cross-run state collisions in the persistent audit log.
const RUN_ID = Date.now();

test('the audit log page shows new entries by polling, without a Refresh button', async ({ page, request }) => {
  const productName = `AuditFruit-${RUN_ID}`;
  await page.goto('/audit-log');
  await expect(page.getByRole('heading', { name: 'Supermarket – Audit Log' })).toBeVisible();
  await expect(page.locator('#app')).not.toBeEmpty();
  await expect(page.getByRole('button', { name: 'Refresh' })).toHaveCount(0);

  const res = await request.post('/api/products/order-fruits', { data: { productName, quantity: 5 } });
  expect(res.ok()).toBeTruthy();

  // no SSE: the page polls every 2 s, so the entry appears without any click
  const entry = page.locator('#audit-log tbody tr').filter({ hasText: productName }).first();
  await expect(entry).toBeVisible({ timeout: 10_000 });
  await expect(page.locator('#audit-log-hint')).toContainText('at most 300');
});
