import { test, expect, Page } from '@playwright/test';

// Unique suffix prevents cross-run state collisions in the persistent Postgres inventory.
const RUN_ID = Date.now();
const fruitName = `Mango-${RUN_ID}`;
const beverageName = `Cola-${RUN_ID}`;

// Inventory updates arrive asynchronously via Kafka, so we reload the page until the
// expected row appears.
async function waitForProductRow(page: Page, productName: string) {
  await expect.poll(
    async () => {
      await page.reload();
      return page.getByRole('cell', { name: productName }).count();
    },
    { message: `product "${productName}" did not appear in inventory`, timeout: 15_000, intervals: [1_000] },
  ).toBeGreaterThan(0);
}

test('products page shows heading and both order forms', async ({ page }) => {
  await page.goto('/products');
  await expect(page.getByRole('heading', { name: 'Supermarket – Products' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Order Fruits' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Order Beverages' })).toBeVisible();
});

test('ordering a fruit adds it to the inventory table', async ({ page }) => {
  await page.goto('/products');

  await page.locator('form[action="/products/order-fruits"] input[name="productName"]').fill(fruitName);
  await page.locator('form[action="/products/order-fruits"] input[name="quantity"]').fill('7');
  await page.locator('form[action="/products/order-fruits"] button[type="submit"]').click();

  // POST redirects back to /products; wait for the redirect to settle.
  await page.waitForURL('/products');

  // Kafka delivery is async — poll until the row arrives.
  await waitForProductRow(page, fruitName);

  const row = page.getByRole('row').filter({ hasText: fruitName });
  await expect(row.getByRole('cell').nth(1)).toHaveText('FRUIT');
  await expect(row.getByRole('cell').nth(2)).toHaveText('7');
});

test('ordering a beverage adds it to the inventory table', async ({ page }) => {
  await page.goto('/products');

  await page.locator('form[action="/products/order-beverages"] input[name="productName"]').fill(beverageName);
  await page.locator('form[action="/products/order-beverages"] input[name="quantity"]').fill('24');
  await page.locator('form[action="/products/order-beverages"] button[type="submit"]').click();

  await page.waitForURL('/products');

  await waitForProductRow(page, beverageName);

  const row = page.getByRole('row').filter({ hasText: beverageName });
  await expect(row.getByRole('cell').nth(1)).toHaveText('BEVERAGE');
  await expect(row.getByRole('cell').nth(2)).toHaveText('24');
});
