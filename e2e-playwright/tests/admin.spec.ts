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

// Inventory updates arrive asynchronously via Kafka, so we reload the page until the
// expected row appears.
async function waitForProductRow(page: Page, productName: string) {
  await expect.poll(
    async () => {
      await page.reload();
      return page.locator('#inventory-body').getByRole('cell', { name: productName }).count();
    },
    { message: `product "${productName}" did not appear in inventory`, timeout: 15_000, intervals: [1_000] },
  ).toBeGreaterThan(0);
}

test('admin page shows heading, supplier sections, and the audit log panel', async ({ page }) => {
  await page.goto('/admin');
  await expect(page.getByRole('heading', { name: 'Supermarket – Admin' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Restock Inventory' })).toBeVisible();
  await expect(page.getByText('REST suppliers')).toBeVisible();
  await expect(page.getByText('SOAP suppliers')).toBeVisible();
  await expect(page.getByText('Kafka supplier')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Audit Log' })).toBeVisible();
});

const RANDOMIZED_NAMES: Record<string, string> = {
  '/admin/order-fruits': 'Mango',
  '/admin/order-vegetables': 'Carrot',
  '/admin/order-dairy': 'Milk',
  '/admin/order-beverages': 'Cola',
  '/admin/order-meat': 'Chicken',
  '/admin/order-bakery': 'Bread',
  '/admin/order-nonfood': 'Detergent',
};

test('randomize button only fills every order form, it submits nothing', async ({ page }) => {
  const orderPosts: string[] = [];
  page.on('request', req => {
    if (req.method() === 'POST' && req.url().includes('/admin/order-')) orderPosts.push(req.url());
  });
  await page.goto('/admin');
  await page.getByRole('button', { name: 'Randomize (dev)' }).click();

  for (const [action, name] of Object.entries(RANDOMIZED_NAMES)) {
    const form = page.locator(`form[action="${action}"]`);
    await expect(form.locator('input[name="productName"]')).toHaveValue(name);
    const qty = Number(await form.locator('input[name="quantity"]').inputValue());
    expect(qty).toBeGreaterThanOrEqual(80);
    expect(qty).toBeLessThanOrEqual(600);
  }

  // Give a (wrongly) triggered htmx submit time to show up before asserting there was none.
  await page.waitForTimeout(500);
  expect(orderPosts).toEqual([]);
});

test('order buttons stay disabled until their form is valid; submit all until every form is', async ({ page }) => {
  await page.goto('/admin');
  const form = page.locator('form[action="/admin/order-fruits"]');
  const orderBtn = form.getByRole('button', { name: 'Order' });
  const submitAll = page.getByRole('button', { name: 'Submit all' });
  await expect(orderBtn).toBeDisabled();
  await expect(submitAll).toBeDisabled();

  await form.locator('input[name="productName"]').fill('Mango');
  await form.locator('input[name="quantity"]').fill('2001'); // above @Max(2000)
  await expect(orderBtn).toBeDisabled();
  await form.locator('input[name="quantity"]').fill('2000');
  await expect(orderBtn).toBeEnabled();
  await expect(submitAll).toBeDisabled(); // the other 6 forms are still empty

  await page.getByRole('button', { name: 'Randomize (dev)' }).click();
  await expect(submitAll).toBeEnabled();
});

test('submit all button sends all 7 filled order forms via htmx without a reload', async ({ page }) => {
  await page.goto('/admin');
  await page.getByRole('button', { name: 'Randomize (dev)' }).click();

  const posts = Object.keys(RANDOMIZED_NAMES).map(action =>
    page.waitForResponse(res => res.request().method() === 'POST' && new URL(res.url()).pathname === action && res.ok()));
  await page.getByRole('button', { name: 'Submit all' }).click();
  await Promise.all(posts);

  // The page never navigates away (no full-page POST) — submission happened without a reload.
  expect(new URL(page.url()).pathname).toBe('/admin');

  await waitForProductRow(page, 'Mango');
  await waitForProductRow(page, 'Detergent');
});

test('ordering a fruit adds it to the inventory table', async ({ page }) => {
  await page.goto('/admin');
  await page.locator('form[action="/admin/order-fruits"] input[name="productName"]').fill(fruit);
  await page.locator('form[action="/admin/order-fruits"] input[name="quantity"]').fill('7');
  await page.locator('form[action="/admin/order-fruits"] button[type="submit"]').click();
  await page.waitForURL('/admin');
  await waitForProductRow(page, fruit);
  const row = page.locator('#inventory-body').getByRole('row').filter({ hasText: fruit });
  await expect(row.getByRole('cell').nth(1)).toHaveText('FRUIT');
  await expect(row.getByRole('cell').nth(2)).toHaveText('7');
});

test('ordering a vegetable adds it to the inventory table', async ({ page }) => {
  await page.goto('/admin');
  await page.locator('form[action="/admin/order-vegetables"] input[name="productName"]').fill(vegetable);
  await page.locator('form[action="/admin/order-vegetables"] input[name="quantity"]').fill('8');
  await page.locator('form[action="/admin/order-vegetables"] button[type="submit"]').click();
  await page.waitForURL('/admin');
  await waitForProductRow(page, vegetable);
  const row = page.locator('#inventory-body').getByRole('row').filter({ hasText: vegetable });
  await expect(row.getByRole('cell').nth(1)).toHaveText('VEGETABLE');
  await expect(row.getByRole('cell').nth(2)).toHaveText('8');
});

test('ordering a dairy product adds it to the inventory table', async ({ page }) => {
  await page.goto('/admin');
  await page.locator('form[action="/admin/order-dairy"] input[name="productName"]').fill(dairy);
  await page.locator('form[action="/admin/order-dairy"] input[name="quantity"]').fill('6');
  await page.locator('form[action="/admin/order-dairy"] button[type="submit"]').click();
  await page.waitForURL('/admin');
  await waitForProductRow(page, dairy);
  const row = page.locator('#inventory-body').getByRole('row').filter({ hasText: dairy });
  await expect(row.getByRole('cell').nth(1)).toHaveText('DAIRY');
  await expect(row.getByRole('cell').nth(2)).toHaveText('6');
});

test('ordering a beverage adds it to the inventory table', async ({ page }) => {
  await page.goto('/admin');
  await page.locator('form[action="/admin/order-beverages"] input[name="productName"]').fill(beverage);
  await page.locator('form[action="/admin/order-beverages"] input[name="quantity"]').fill('24');
  await page.locator('form[action="/admin/order-beverages"] button[type="submit"]').click();
  await page.waitForURL('/admin');
  await waitForProductRow(page, beverage);
  const row = page.locator('#inventory-body').getByRole('row').filter({ hasText: beverage });
  await expect(row.getByRole('cell').nth(1)).toHaveText('BEVERAGE');
  await expect(row.getByRole('cell').nth(2)).toHaveText('24');
});

test('ordering a meat product adds it to the inventory table', async ({ page }) => {
  await page.goto('/admin');
  await page.locator('form[action="/admin/order-meat"] input[name="productName"]').fill(meat);
  await page.locator('form[action="/admin/order-meat"] input[name="quantity"]').fill('4');
  await page.locator('form[action="/admin/order-meat"] button[type="submit"]').click();
  await page.waitForURL('/admin');
  await waitForProductRow(page, meat);
  const row = page.locator('#inventory-body').getByRole('row').filter({ hasText: meat });
  await expect(row.getByRole('cell').nth(1)).toHaveText('MEAT');
  await expect(row.getByRole('cell').nth(2)).toHaveText('4');
});

test('ordering a bakery product adds it to the inventory table', async ({ page }) => {
  await page.goto('/admin');
  await page.locator('form[action="/admin/order-bakery"] input[name="productName"]').fill(bakery);
  await page.locator('form[action="/admin/order-bakery"] input[name="quantity"]').fill('10');
  await page.locator('form[action="/admin/order-bakery"] button[type="submit"]').click();
  await page.waitForURL('/admin');
  await waitForProductRow(page, bakery);
  const row = page.locator('#inventory-body').getByRole('row').filter({ hasText: bakery });
  await expect(row.getByRole('cell').nth(1)).toHaveText('BAKERY');
  await expect(row.getByRole('cell').nth(2)).toHaveText('10');
});

test('ordering a non-food product adds it to the inventory table', async ({ page }) => {
  await page.goto('/admin');
  await page.locator('form[action="/admin/order-nonfood"] input[name="productName"]').fill(nonfood);
  await page.locator('form[action="/admin/order-nonfood"] input[name="quantity"]').fill('3');
  await page.locator('form[action="/admin/order-nonfood"] button[type="submit"]').click();
  await page.waitForURL('/admin');
  await waitForProductRow(page, nonfood);
  const row = page.locator('#inventory-body').getByRole('row').filter({ hasText: nonfood });
  await expect(row.getByRole('cell').nth(1)).toHaveText('NON_FOOD');
  await expect(row.getByRole('cell').nth(2)).toHaveText('3');
});
