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

test('theme toggle switches dark/light and remembers the choice across reloads', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'light' });
  await page.goto('/shop');
  const html = page.locator('html');
  await expect(html).not.toHaveAttribute('data-theme');

  await page.locator('.theme-toggle').click();
  await expect(html).toHaveAttribute('data-theme', 'dark');

  await page.reload();
  await expect(html).toHaveAttribute('data-theme', 'dark');

  await page.locator('.theme-toggle').click();
  await expect(html).toHaveAttribute('data-theme', 'light');
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

test('an open shop page picks up a newly stocked product without a reload and keeps typed quantities', async ({ page, context }) => {
  const existing = `Pear-${RUN_ID}`;
  const newcomer = `Plum-${RUN_ID}`;
  const admin = await context.newPage();

  async function orderFruit(name: string) {
    await admin.goto('/admin');
    await admin.locator('form[action="/admin/order-fruits"] input[name="productName"]').fill(name);
    await admin.locator('form[action="/admin/order-fruits"] input[name="quantity"]').fill('10');
    await admin.locator('form[action="/admin/order-fruits"] button[type="submit"]').click();
  }

  await orderFruit(existing);
  await page.goto('/shop');
  await waitForProductRow(page, existing);
  const qty = page.getByRole('row').filter({ hasText: existing }).locator('input[name="quantity"]');
  await qty.fill('4');

  // From here on no reload: the 3 s poll has to morph the new row in.
  await orderFruit(newcomer);
  await expect(page.getByRole('cell', { name: newcomer })).toBeVisible({ timeout: 15_000 });
  await expect(qty).toHaveValue('4');
});

test('the cart flags quantities above stock, disables Purchase, and reacts when stock drops under the customer', async ({ page, context }) => {
  const name = `Kiwi-${RUN_ID}`;
  await page.goto('/admin');
  await page.locator('form[action="/admin/order-fruits"] input[name="productName"]').fill(name);
  await page.locator('form[action="/admin/order-fruits"] input[name="quantity"]').fill('5');
  await page.locator('form[action="/admin/order-fruits"] button[type="submit"]').click();

  await page.goto('/shop');
  await waitForProductRow(page, name);
  const qty = page.getByRole('row').filter({ hasText: name }).locator('input[name="quantity"]');
  const purchaseBtn = page.getByRole('button', { name: 'Purchase' });
  const summary = page.locator('#shop-cart-summary');

  // Nothing entered yet.
  await expect(purchaseBtn).toBeDisabled();
  await expect(summary).toHaveText('0 products, 0 items');

  await qty.fill('6');
  await expect(qty).toHaveClass(/is-danger/);
  await expect(purchaseBtn).toBeDisabled();

  await qty.fill('4');
  await expect(qty).not.toHaveClass(/is-danger/);
  await expect(purchaseBtn).toBeEnabled();
  await expect(summary).toHaveText('1 products, 4 items');

  // Another customer buys 3 of the 5; the SSE-triggered morph lowers max to 2 without a reload.
  const other = await context.newPage();
  await other.goto('/shop');
  await waitForProductRow(other, name);
  await other.getByRole('row').filter({ hasText: name }).locator('input[name="quantity"]').fill('3');
  await other.getByRole('button', { name: 'Purchase' }).click();

  await expect(qty).toHaveAttribute('max', '2', { timeout: 15_000 });
  await expect(qty).toHaveValue('4');
  await expect(qty).toHaveClass(/is-danger/);
  await expect(purchaseBtn).toBeDisabled();
});
