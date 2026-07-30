import { test, expect, Page } from '@playwright/test';

// Unique suffix prevents cross-run state collisions in the persistent Postgres inventory.
const RUN_ID = Date.now();
const fruit     = `Mango-${RUN_ID}`;
const vegetable = `Carrot-${RUN_ID}`;
const dairy     = `Milk-${RUN_ID}`;
const beverage  = `Cola-${RUN_ID}`;
const meat      = `Chicken-${RUN_ID}`;
const bakery    = `Bread-${RUN_ID}`;
const nonfood   = `Detergent-${RUN_ID}`;
const purchase  = `Apple-${RUN_ID}`;

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

test('products page shows heading and supplier sections', async ({ page }) => {
  await page.goto('/products');
  await expect(page.getByRole('heading', { name: 'Supermarket – Products' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Restock Inventory' })).toBeVisible();
  await expect(page.getByText('REST suppliers')).toBeVisible();
  await expect(page.getByText('SOAP suppliers')).toBeVisible();
  await expect(page.getByText('Kafka supplier')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Simulate Customer Purchase' })).toBeVisible();
});

test('ordering a fruit adds it to the inventory table', async ({ page }) => {
  await page.goto('/products');
  await page.locator('form[action="/products/order-fruits"] input[name="productName"]').fill(fruit);
  await page.locator('form[action="/products/order-fruits"] input[name="quantity"]').fill('7');
  await page.locator('form[action="/products/order-fruits"] button[type="submit"]').click();
  await page.waitForURL('/products');
  await waitForProductRow(page, fruit);
  const row = page.getByRole('row').filter({ hasText: fruit });
  await expect(row.getByRole('cell').nth(1)).toHaveText('FRUIT');
  await expect(row.getByRole('cell').nth(2)).toHaveText('7');
});

test('ordering a vegetable adds it to the inventory table', async ({ page }) => {
  await page.goto('/products');
  await page.locator('form[action="/products/order-vegetables"] input[name="productName"]').fill(vegetable);
  await page.locator('form[action="/products/order-vegetables"] input[name="quantity"]').fill('8');
  await page.locator('form[action="/products/order-vegetables"] button[type="submit"]').click();
  await page.waitForURL('/products');
  await waitForProductRow(page, vegetable);
  const row = page.getByRole('row').filter({ hasText: vegetable });
  await expect(row.getByRole('cell').nth(1)).toHaveText('VEGETABLE');
  await expect(row.getByRole('cell').nth(2)).toHaveText('8');
});

test('ordering a dairy product adds it to the inventory table', async ({ page }) => {
  await page.goto('/products');
  await page.locator('form[action="/products/order-dairy"] input[name="productName"]').fill(dairy);
  await page.locator('form[action="/products/order-dairy"] input[name="quantity"]').fill('6');
  await page.locator('form[action="/products/order-dairy"] button[type="submit"]').click();
  await page.waitForURL('/products');
  await waitForProductRow(page, dairy);
  const row = page.getByRole('row').filter({ hasText: dairy });
  await expect(row.getByRole('cell').nth(1)).toHaveText('DAIRY');
  await expect(row.getByRole('cell').nth(2)).toHaveText('6');
});

test('ordering a beverage adds it to the inventory table', async ({ page }) => {
  await page.goto('/products');
  await page.locator('form[action="/products/order-beverages"] input[name="productName"]').fill(beverage);
  await page.locator('form[action="/products/order-beverages"] input[name="quantity"]').fill('24');
  await page.locator('form[action="/products/order-beverages"] button[type="submit"]').click();
  await page.waitForURL('/products');
  await waitForProductRow(page, beverage);
  const row = page.getByRole('row').filter({ hasText: beverage });
  await expect(row.getByRole('cell').nth(1)).toHaveText('BEVERAGE');
  await expect(row.getByRole('cell').nth(2)).toHaveText('24');
});

test('ordering a meat product adds it to the inventory table', async ({ page }) => {
  await page.goto('/products');
  await page.locator('form[action="/products/order-meat"] input[name="productName"]').fill(meat);
  await page.locator('form[action="/products/order-meat"] input[name="quantity"]').fill('4');
  await page.locator('form[action="/products/order-meat"] button[type="submit"]').click();
  await page.waitForURL('/products');
  await waitForProductRow(page, meat);
  const row = page.getByRole('row').filter({ hasText: meat });
  await expect(row.getByRole('cell').nth(1)).toHaveText('MEAT');
  await expect(row.getByRole('cell').nth(2)).toHaveText('4');
});

test('ordering a bakery product adds it to the inventory table', async ({ page }) => {
  await page.goto('/products');
  await page.locator('form[action="/products/order-bakery"] input[name="productName"]').fill(bakery);
  await page.locator('form[action="/products/order-bakery"] input[name="quantity"]').fill('10');
  await page.locator('form[action="/products/order-bakery"] button[type="submit"]').click();
  await page.waitForURL('/products');
  await waitForProductRow(page, bakery);
  const row = page.getByRole('row').filter({ hasText: bakery });
  await expect(row.getByRole('cell').nth(1)).toHaveText('BAKERY');
  await expect(row.getByRole('cell').nth(2)).toHaveText('10');
});

test('ordering a non-food product adds it to the inventory table', async ({ page }) => {
  await page.goto('/products');
  await page.locator('form[action="/products/order-nonfood"] input[name="productName"]').fill(nonfood);
  await page.locator('form[action="/products/order-nonfood"] input[name="quantity"]').fill('3');
  await page.locator('form[action="/products/order-nonfood"] button[type="submit"]').click();
  await page.waitForURL('/products');
  await waitForProductRow(page, nonfood);
  const row = page.getByRole('row').filter({ hasText: nonfood });
  await expect(row.getByRole('cell').nth(1)).toHaveText('NON_FOOD');
  await expect(row.getByRole('cell').nth(2)).toHaveText('3');
});

test('purchasing a product deducts its inventory', async ({ page }) => {
  // Stock up first.
  await page.goto('/products');
  await page.locator('form[action="/products/order-fruits"] input[name="productName"]').fill(purchase);
  await page.locator('form[action="/products/order-fruits"] input[name="quantity"]').fill('10');
  await page.locator('form[action="/products/order-fruits"] button[type="submit"]').click();
  await page.waitForURL('/products');
  await waitForProductRow(page, purchase);

  // Now purchase 3 units.
  await page.locator('form[action="/products/purchase"] input[name="productName"]').fill(purchase);
  await page.locator('form[action="/products/purchase"] input[name="quantity"]').fill('3');
  await page.locator('form[action="/products/purchase"] button[type="submit"]').click();
  await page.waitForURL('/products');

  await waitForProductAmount(page, purchase, '7');
  const row = page.getByRole('row').filter({ hasText: purchase });
  await expect(row.getByRole('cell').nth(2)).toHaveText('7');
});
