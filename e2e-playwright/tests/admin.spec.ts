import { test, expect, Page, APIRequestContext, Locator } from '@playwright/test';

// Unique suffix prevents cross-run state collisions in the persistent Postgres inventory.
const RUN_ID = Date.now();

// The radios are rendered as Bulma buttons: the native input is visually hidden, so choose one like a user does,
// by clicking its button (the wrapping label), which hx-live then highlights with is-link.
async function choose(radio: Locator) {
  const button = radio.locator('..');
  await button.click();
  await expect(radio).toBeChecked();
  await expect(button).toHaveClass(/\bis-link\b/);
}

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

// The page has two top-level tabs, "Inventory" (selected initially) and "Manual restock"; only the active one is visible.
async function openTab(page: Page, name: string) {
  const tab = page.getByRole('tab', { name, exact: true });
  await tab.click();
  await expect(tab).toHaveAttribute('aria-selected', 'true');
}

// The order forms are on the "Manual restock" tab, grouped into supplier tabs, and only the active tab's panel is
// visible, so open the supplier tab whose panel (aria-controls) - the form's nearest tabpanel - contains the form, too.
async function orderForm(page: Page, action: string) {
  await openTab(page, 'Manual restock');
  const form = page.locator(`form[action="${action}"]`);
  const panelId = await form.locator('xpath=ancestor::*[@role="tabpanel"][1]').getAttribute('id');
  await page.locator(`[role="tab"][aria-controls="${panelId}"]`).click();
  await expect(form).toBeVisible();
  return form;
}

test('admin page shows heading and supplier tabs, but no audit log', async ({ page }) => {
  await page.goto('/admin');
  await expect(page.getByRole('heading', { name: 'Supermarket – Admin' })).toBeVisible();
  await expect(page.getByRole('tab', { name: 'Inventory', exact: true })).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByRole('heading', { name: 'Current Inventory' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Restock Inventory' })).toBeHidden();

  await openTab(page, 'Manual restock');
  await expect(page.getByRole('heading', { name: 'Current Inventory' })).toBeHidden();
  await expect(page.getByRole('heading', { name: 'Restock Inventory' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'DC Inventory' })).toBeVisible();
  await expect(page.getByRole('tab', { name: 'REST suppliers' })).toBeVisible();
  await expect(page.getByRole('tab', { name: 'SOAP suppliers' })).toBeVisible();
  await expect(page.getByRole('tab', { name: 'Kafka supplier' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Audit Log' })).toHaveCount(0);
});

test('only the selected supplier tab shows its order forms, REST is selected initially', async ({ page }) => {
  await page.goto('/admin');
  await openTab(page, 'Manual restock');
  const fruits = page.locator('form[action="/admin/order-fruits"]');
  const beverages = page.locator('form[action="/admin/order-beverages"]');
  const rest = page.getByRole('tab', { name: 'REST suppliers' });
  const soap = page.getByRole('tab', { name: 'SOAP suppliers' });
  await expect(rest).toHaveAttribute('aria-selected', 'true');
  await expect(fruits).toBeVisible();
  await expect(beverages).toBeHidden();

  await choose(fruits.getByRole('radio', { name: 'Mango', exact: true }));
  await soap.click();
  await expect(soap).toHaveAttribute('aria-selected', 'true');
  await expect(rest).toHaveAttribute('aria-selected', 'false');
  await expect(beverages).toBeVisible();
  await expect(fruits).toBeHidden();

  await rest.click(); // switching back keeps what was chosen
  await expect(fruits.getByRole('radio', { name: 'Mango', exact: true })).toBeChecked();
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
    const form = await orderForm(page, action);
    await expect(form.locator('input[name="productName"]')).toHaveCount(products.length);
    for (const product of products) await expect(form.getByRole('radio', { name: product, exact: true })).toBeVisible();
    for (const qty of PRESET_QUANTITIES) await expect(form.getByRole('radio', { name: qty, exact: true })).toBeVisible();
    await expect(form.locator('input[type="radio"]:checked')).toHaveCount(0);
  }
});

test('order buttons stay disabled until their form is valid', async ({ page }) => {
  await page.goto('/admin');
  const form = await orderForm(page, '/admin/order-fruits');
  const orderBtn = form.getByRole('button', { name: 'Order' });
  await expect(orderBtn).toBeDisabled();

  await choose(form.getByRole('radio', { name: 'Mango', exact: true }));
  await expect(orderBtn).toBeDisabled(); // no quantity yet
  await choose(form.getByRole('radio', { name: '100', exact: true }));
  await expect(orderBtn).toBeEnabled();

  await choose(form.getByRole('radio', { name: 'Custom quantity' }));
  await expect(orderBtn).toBeDisabled(); // custom chosen, but nothing typed
  await expect(form.getByRole('radio', { name: '100', exact: true }).locator('..')).not.toHaveClass(/\bis-link\b/);
  const custom = form.locator('.qty-custom-input');
  await custom.fill('2001'); // above @Max(2000)
  await expect(orderBtn).toBeDisabled();
  await custom.fill('2000');
  await expect(orderBtn).toBeEnabled();
});

test('the custom quantity input is readonly until its radio is chosen, and focusing it chooses the radio', async ({ page }) => {
  await page.goto('/admin');
  const form = await orderForm(page, '/admin/order-fruits');
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
  await choose(form.getByRole('radio', { name: 'Banana', exact: true }));
  await choose(form.getByRole('radio', { name: '50', exact: true }));
  await expect(custom).not.toBeEditable();
  await expect(form.getByRole('button', { name: 'Order' })).toBeEnabled();
});

// The fixed products are shared across runs, so each test checks that the amount grew by at
// least the ordered quantity rather than for an exact value. Fruits use the custom quantity, the others a preset.
for (const { action, type, products } of ORDER_FORMS) {
  const product = products[0];
  const custom = type === 'FRUIT';
  const qty = custom ? 7 : 10;
  test(`ordering ${product} (${type}) with a ${custom ? 'custom' : 'preset'} quantity raises its inventory`, async ({ page }) => {
    await page.goto('/admin');
    await expect(page.locator('#admin-inventory')).not.toBeEmpty();
    const before = await availableAmount(page, product);

    const form = await orderForm(page, action);
    await choose(form.getByRole('radio', { name: product, exact: true }));
    if (custom) {
      await choose(form.getByRole('radio', { name: 'Custom quantity' }));
      await form.locator('.qty-custom-input').fill(String(qty));
    } else {
      await choose(form.getByRole('radio', { name: String(qty), exact: true }));
    }
    await form.getByRole('button', { name: 'Order' }).click();

    await openTab(page, 'Inventory');
    await waitForProductRow(page, product);
    await expect(inventoryRow(page, product).getByRole('cell').nth(1)).toHaveText(type);
    await expect.poll(() => availableAmount(page, product), { timeout: 15_000 }).toBeGreaterThanOrEqual(before + qty);
    expect(new URL(page.url()).pathname).toBe('/admin');
  });
}

test('the inventory tab is read-only: its rows have no restock buttons', async ({ page }) => {
  const name = `Date-${RUN_ID}`;
  await page.goto('/admin');
  await stockFruit(page.request, name, 5);
  await waitForProductRow(page, name);

  await expect(inventoryRow(page, name).getByRole('button')).toHaveCount(0);
});

test('clicking a quantity button of a DC inventory row restocks that amount at the DC, live', async ({ page }) => {
  const name = `Fig-${RUN_ID}`;
  await page.goto('/admin');
  await openTab(page, 'Manual restock');
  await stockFruit(page.request, name, 5);

  // Name, Type, DC, Restock DC
  const row = page.locator('#admin-dc-inventory tbody tr').filter({ has: page.getByRole('cell', { name, exact: true }) });
  await expect(row).toBeVisible({ timeout: 15_000 });
  await expect(row.getByRole('cell')).toHaveCount(5);
  const available = async () => Number(await row.getByRole('cell').nth(2).textContent());
  await expect.poll(available, { timeout: 15_000 }).toBe(5);
  await row.getByRole('button', { name: '20', exact: true }).click();

  // No reload: the delivery arrives via Kafka and the SSE-triggered morph updates the row.
  await expect.poll(available, { timeout: 15_000 }).toBe(25);
});

// The DC learns its levels like the stores: a fresh product starts with the cold-start levels (min 136 / max 316), so 5
// is below min and red. (Automatic supplier orders are off on the e2e server, so nothing tops it up.)
test('a DC cell shows its learned levels and is red below min', async ({ page }) => {
  const name = `Kiwi-${RUN_ID}`;
  await page.goto('/admin');
  await stockFruit(page.request, name, 5);
  await waitForProductRow(page, name);

  const dcCell = inventoryRow(page, name).getByRole('cell').nth(2);
  await expect(dcCell).toHaveAttribute('title', 'min 136 / max 316');
  await expect(dcCell).toHaveClass(/has-text-danger/);
});

// The stubs deliver right away, so an order is open only for a moment; that it is listed while open is covered by
// SupplierOrderFlowTest. Here: the section is there, and live.
test('the supplier orders section lists no open order once the delivery has arrived', async ({ page }) => {
  const name = `Lime-${RUN_ID}`;
  await page.goto('/admin');
  await expect(page.getByRole('heading', { name: 'Supplier Orders' })).toBeVisible();
  await stockFruit(page.request, name, 5);
  await waitForProductRow(page, name);

  await expect(page.locator('#admin-supplier-orders').getByRole('cell', { name, exact: false })).toHaveCount(0, { timeout: 15_000 });
});

// The real reset would wipe the data of the other spec files, which run in parallel workers, so the POST is answered
// here instead of by the server; AdminReceiverTest covers what the reset deletes.
test('reset demo data asks for confirmation before it posts', async ({ page }) => {
  const posts: string[] = [];
  await page.route('**/admin/reset', async (route) => {
    posts.push(route.request().method());
    await route.fulfill({ status: 200, contentType: 'text/html', body: '' });
  });
  await page.goto('/admin');
  const reset = page.getByRole('button', { name: 'Reset demo data' });

  page.once('dialog', (dialog) => dialog.dismiss());
  await reset.click();
  await page.waitForTimeout(300);
  expect(posts).toEqual([]);

  page.once('dialog', (dialog) => {
    expect(dialog.message()).toContain('audit log');
    return dialog.accept();
  });
  await reset.click();
  await expect.poll(() => posts).toEqual(['POST']);
});
