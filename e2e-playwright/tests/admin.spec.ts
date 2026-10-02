import { test, expect, Page, APIRequestContext } from '@playwright/test';

// Unique suffix prevents cross-run state collisions in the persistent Postgres inventory.
const RUN_ID = Date.now();

// Inventory updates arrive asynchronously via Kafka; the panel picks them up through the SSE stream,
// so no reload: waiting for the row also proves the panel is live.
async function waitForProductRow(page: Page, productName: string) {
  await expect(page.locator('#admin-inventory').getByRole('cell', { name: productName, exact: true }).first())
    .toBeVisible({ timeout: 15_000 });
}

function inventoryRow(page: Page, productName: string) {
  return page.locator('#admin-inventory tbody tr').filter({ has: page.getByRole('cell', { name: productName, exact: true }) });
}

// 0 while the product has no inventory row yet.
async function availableAmount(page: Page, productName: string): Promise<number> {
  const row = inventoryRow(page, productName);
  return await row.count() === 0 ? 0 : Number(await row.getByRole('cell').nth(2).textContent());
}

// The order forms only offer a fixed choice of products, so unique test products are stocked via the JSON API.
async function stockFruit(request: APIRequestContext, productName: string, quantity: number) {
  const res = await request.post('/api/products/order-fruits', { data: { productName, quantity } });
  expect(res.ok()).toBeTruthy();
}

function orderForm(page: Page, action: string) {
  return page.locator(`form[action="${action}"]`);
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

const ORDER_FORMS: { action: string, type: string, products: string[] }[] = [
  { action: '/admin/order-fruits', type: 'FRUIT', products: ['Mango', 'Banana', 'Apple', 'Orange'] },
  { action: '/admin/order-vegetables', type: 'VEGETABLE', products: ['Carrot', 'Potato', 'Tomato', 'Cucumber'] },
  { action: '/admin/order-dairy', type: 'DAIRY', products: ['Milk', 'Cheese', 'Yogurt', 'Butter'] },
  { action: '/admin/order-beverages', type: 'BEVERAGE', products: ['Cola', 'Water', 'Juice', 'Beer'] },
  { action: '/admin/order-meat', type: 'MEAT', products: ['Chicken', 'Beef', 'Pork', 'Lamb'] },
  { action: '/admin/order-bakery', type: 'BAKERY', products: ['Bread', 'Croissant', 'Baguette', 'Pretzel'] },
  { action: '/admin/order-nonfood', type: 'NON_FOOD', products: ['Detergent', 'Soap', 'Sponge', 'Paper towels'] },
];
const PRESET_QUANTITIES = ['10', '50', '100', '500'];

test('every order form offers its products and the preset quantities as radio buttons, nothing preselected', async ({ page }) => {
  await page.goto('/admin');
  for (const { action, products } of ORDER_FORMS) {
    const form = orderForm(page, action);
    await expect(form.locator('input[name="productName"]')).toHaveCount(products.length);
    for (const product of products) await expect(form.getByRole('radio', { name: product, exact: true })).toBeVisible();
    for (const qty of PRESET_QUANTITIES) await expect(form.getByRole('radio', { name: qty, exact: true })).toBeVisible();
    await expect(form.locator('input[type="radio"]:checked')).toHaveCount(0);
  }
});

test('randomize button only fills every order form, it submits nothing', async ({ page }) => {
  const orderPosts: string[] = [];
  page.on('request', req => {
    if (req.method() === 'POST' && req.url().includes('/admin/order-')) orderPosts.push(req.url());
  });
  await page.goto('/admin');
  await page.getByRole('button', { name: 'Randomize (dev)' }).click();

  for (const { action, products } of ORDER_FORMS) {
    const form = orderForm(page, action);
    expect(products).toContain(await form.locator('input[name="productName"]:checked').inputValue());
    const quantity = form.locator('input[name="quantity"]:checked');
    const qty = await quantity.inputValue();
    if (await quantity.evaluate(r => r.classList.contains('qty-custom'))) {
      expect(Number(qty)).toBeGreaterThanOrEqual(80);
      expect(Number(qty)).toBeLessThanOrEqual(600);
      await expect(form.locator('.qty-custom-input')).toHaveValue(qty);
    } else {
      expect(PRESET_QUANTITIES).toContain(qty);
    }
  }

  // Give a (wrongly) triggered htmx submit time to show up before asserting there was none.
  await page.waitForTimeout(500);
  expect(orderPosts).toEqual([]);
});

test('order buttons stay disabled until their form is valid; submit all until every form is', async ({ page }) => {
  await page.goto('/admin');
  const form = orderForm(page, '/admin/order-fruits');
  const orderBtn = form.getByRole('button', { name: 'Order' });
  const submitAll = page.getByRole('button', { name: 'Submit all' });
  await expect(orderBtn).toBeDisabled();
  await expect(submitAll).toBeDisabled();

  await form.getByRole('radio', { name: 'Mango', exact: true }).check();
  await expect(orderBtn).toBeDisabled(); // no quantity yet
  await form.getByRole('radio', { name: '100', exact: true }).check();
  await expect(orderBtn).toBeEnabled();

  await form.getByRole('radio', { name: 'Custom quantity' }).check();
  await expect(orderBtn).toBeDisabled(); // custom chosen, but nothing typed
  const custom = form.locator('.qty-custom-input');
  await custom.fill('2001'); // above @Max(2000)
  await expect(orderBtn).toBeDisabled();
  await custom.fill('2000');
  await expect(orderBtn).toBeEnabled();
  await expect(submitAll).toBeDisabled(); // the other 6 forms are still empty

  await page.getByRole('button', { name: 'Randomize (dev)' }).click();
  await expect(submitAll).toBeEnabled();
});

test('the custom quantity input is readonly until its radio is chosen, and focusing it chooses the radio', async ({ page }) => {
  await page.goto('/admin');
  const form = orderForm(page, '/admin/order-fruits');
  const customRadio = form.getByRole('radio', { name: 'Custom quantity' });
  const custom = form.locator('.qty-custom-input');
  await expect(custom).not.toBeEditable();

  await custom.focus();
  await expect(customRadio).toBeChecked();
  await expect(custom).toBeEditable();
  await custom.fill('42');
  await expect(customRadio).toHaveValue('42');

  // An out-of-range leftover must not block a preset quantity: the readonly input is exempt from validation.
  await custom.fill('5000');
  await form.getByRole('radio', { name: 'Banana', exact: true }).check();
  await form.getByRole('radio', { name: '50', exact: true }).check();
  await expect(custom).not.toBeEditable();
  await expect(form.getByRole('button', { name: 'Order' })).toBeEnabled();
});

test('submit all button sends all 7 filled order forms via htmx without a reload', async ({ page }) => {
  await page.goto('/admin');
  await page.getByRole('button', { name: 'Randomize (dev)' }).click();
  const fruit = await orderForm(page, '/admin/order-fruits').locator('input[name="productName"]:checked').inputValue();
  const nonfood = await orderForm(page, '/admin/order-nonfood').locator('input[name="productName"]:checked').inputValue();

  const posts = ORDER_FORMS.map(({ action }) =>
    page.waitForResponse(res => res.request().method() === 'POST' && new URL(res.url()).pathname === action && res.ok()));
  await page.getByRole('button', { name: 'Submit all' }).click();
  await Promise.all(posts);

  // The page never navigates away (no full-page POST) — submission happened without a reload.
  expect(new URL(page.url()).pathname).toBe('/admin');

  await waitForProductRow(page, fruit);
  await waitForProductRow(page, nonfood);
});

// The fixed products are shared across runs (and with "Submit all"), so each test checks that the amount grew by at
// least the ordered quantity rather than for an exact value. Fruits use the custom quantity, the others a preset.
for (const { action, type, products } of ORDER_FORMS) {
  const product = products[0];
  const custom = type === 'FRUIT';
  const qty = custom ? 7 : 10;
  test(`ordering ${product} (${type}) with a ${custom ? 'custom' : 'preset'} quantity raises its inventory`, async ({ page }) => {
    await page.goto('/admin');
    await expect(page.locator('#admin-inventory')).not.toBeEmpty();
    const before = await availableAmount(page, product);

    const form = orderForm(page, action);
    await form.getByRole('radio', { name: product, exact: true }).check();
    if (custom) {
      await form.getByRole('radio', { name: 'Custom quantity' }).check();
      await form.locator('.qty-custom-input').fill(String(qty));
    } else {
      await form.getByRole('radio', { name: String(qty), exact: true }).check();
    }
    await form.getByRole('button', { name: 'Order' }).click();

    await waitForProductRow(page, product);
    await expect(inventoryRow(page, product).getByRole('cell').nth(1)).toHaveText(type);
    await expect.poll(() => availableAmount(page, product), { timeout: 15_000 }).toBeGreaterThanOrEqual(before + qty);
    expect(new URL(page.url()).pathname).toBe('/admin');
  });
}

test('restocking from an inventory row raises its amount live and keeps quantities typed into other rows', async ({ page }) => {
  const restocked = `Fig-${RUN_ID}`;
  const other = `Lime-${RUN_ID}`;
  await page.goto('/admin');
  for (const name of [restocked, other]) await stockFruit(page.request, name, 5);
  await waitForProductRow(page, restocked);
  await waitForProductRow(page, other);

  const otherQty = inventoryRow(page, other).locator('input[name="quantity"]');
  await otherQty.fill('33');

  const row = inventoryRow(page, restocked);
  const available = async () => Number(await row.getByRole('cell').nth(2).textContent());
  const before = await available();
  await row.locator('input[name="quantity"]').fill('100');
  await row.getByRole('button', { name: 'Restock' }).click();

  // No reload: the delivery arrives via Kafka and the SSE-triggered morph updates the row.
  await expect.poll(available, { timeout: 15_000 }).toBeGreaterThan(before);
  await expect(otherQty).toHaveValue('33');
  await expect(row.locator('input[name="quantity"]')).toHaveValue('100'); // kept for another restock
});

test('the restock button of a row is disabled while its quantity is outside 1-2000', async ({ page }) => {
  const name = `Date-${RUN_ID}`;
  await page.goto('/admin');
  await stockFruit(page.request, name, 5);
  await waitForProductRow(page, name);

  const row = inventoryRow(page, name);
  const qty = row.locator('input[name="quantity"]');
  const restock = row.getByRole('button', { name: 'Restock' });
  await expect(restock).toBeDisabled();
  await qty.fill('0');
  await expect(restock).toBeDisabled();
  await qty.fill('2001');
  await expect(restock).toBeDisabled();
  await qty.fill('2000');
  await expect(restock).toBeEnabled();
});
