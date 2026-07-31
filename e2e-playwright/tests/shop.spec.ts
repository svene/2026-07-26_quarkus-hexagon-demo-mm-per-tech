import { test, expect, Page } from '@playwright/test';

// Unique suffix prevents cross-run state collisions in the persistent Postgres inventory.
const RUN_ID = Date.now();
const purchase = `Apple-${RUN_ID}`;

// Inventory updates arrive asynchronously via Kafka, so we reload the page until the
// expected row appears.
async function waitForProductRow(page: Page, productName: string) {
  await expect.poll(
    async () => {
      await page.reload();
      return page.getByRole('cell', { name: productName }).count();
    },
    { message: `product "${productName}" did not appear in the shop`, timeout: 15_000, intervals: [1_000] },
  ).toBeGreaterThan(0);
}

async function waitForProductAmount(page: Page, productName: string, amount: string) {
  await expect.poll(
    async () => {
      await page.reload();
      const row = page.getByRole('row').filter({ hasText: productName });
      return row.getByRole('cell').nth(2).textContent();
    },
    { message: `product "${productName}" did not reach amount ${amount}`, timeout: 15_000, intervals: [1_000] },
  ).toBe(amount);
}

test('shop page shows heading and empty-cart message when there is no stock', async ({ page }) => {
  await page.goto('/shop');
  await expect(page.getByRole('heading', { name: 'Supermarket – Shop' })).toBeVisible();
});

test('purchasing a product deducts its inventory', async ({ page }) => {
  // Stock up first via the admin page.
  await page.goto('/admin');
  await page.locator('form[action="/admin/order-fruits"] input[name="productName"]').fill(purchase);
  await page.locator('form[action="/admin/order-fruits"] input[name="quantity"]').fill('10');
  await page.locator('form[action="/admin/order-fruits"] button[type="submit"]').click();
  await page.waitForURL('/admin');
  await waitForProductRow(page, purchase);

  // Now buy 3 units through the shop's cart-style form.
  await page.goto('/shop');
  await waitForProductRow(page, purchase);
  const row = page.getByRole('row').filter({ hasText: purchase });
  await row.locator('input[name="quantity"]').fill('3');
  await page.getByRole('button', { name: 'Purchase' }).click();
  await page.waitForURL('/shop');

  await waitForProductAmount(page, purchase, '7');
  const updatedRow = page.getByRole('row').filter({ hasText: purchase });
  await expect(updatedRow.getByRole('cell').nth(2)).toHaveText('7');
});
