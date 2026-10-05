import { test, expect } from '@playwright/test';

// Unique suffix prevents cross-run state collisions in the persistent audit log.
const RUN_ID = Date.now();

test('the audit log page shows new entries only after Refresh', async ({ page, request }) => {
  const productName = `AuditFruit-${RUN_ID}`;
  await page.goto('/audit-log');
  await expect(page.getByRole('heading', { name: 'Supermarket – Audit Log' })).toBeVisible();
  await expect(page.locator('#app')).not.toBeEmpty();

  const res = await request.post('/api/products/order-fruits', { data: { productName, quantity: 5 } });
  expect(res.ok()).toBeTruthy();

  // no SSE and no polling: the entry stays invisible until the page is refreshed
  const entry = page.locator('#audit-log tbody tr').filter({ hasText: productName }).first();
  await expect(entry).toHaveCount(0);
  await expect(async () => {
    await page.locator('#refresh-audit-log').click();
    await expect(entry).toBeVisible({ timeout: 1_000 });
  }).toPass({ timeout: 15_000 });
  await expect(page.locator('#audit-log-hint')).toContainText('at most 300');
});
