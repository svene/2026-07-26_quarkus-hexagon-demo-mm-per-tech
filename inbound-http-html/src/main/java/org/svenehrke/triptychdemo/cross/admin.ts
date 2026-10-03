import {html} from "hono/html";
import type {
	AdminInventoryVM,
	AdminPageVM,
	AuditEntryVM,
	AuditPanelVM,
	OrderErrorsVM,
	ProductRowVM
} from "./generated/vm-types";
import type {HtmlResult} from "./route-types";

type OrderForm = { label: string, action: string, products: string[] };
type SupplierBox = { title: string, adapter: string, forms: OrderForm[] };

const PRESET_QUANTITIES = [10, 50, 100, 500];

const SUPPLIER_BOXES: SupplierBox[] = [
	{
		title: "REST suppliers", adapter: "outbound-httpclient", forms: [
			{label: "Fruits", action: "/admin/order-fruits", products: ["Mango", "Banana", "Apple", "Orange"]},
			{label: "Vegetables", action: "/admin/order-vegetables", products: ["Carrot", "Potato", "Tomato", "Cucumber"]},
			{label: "Dairy", action: "/admin/order-dairy", products: ["Milk", "Cheese", "Yogurt", "Butter"]},
		]
	},
	{
		title: "SOAP suppliers", adapter: "outbound-webservice", forms: [
			{label: "Beverages", action: "/admin/order-beverages", products: ["Cola", "Water", "Juice", "Beer"]},
			{label: "Meat", action: "/admin/order-meat", products: ["Chicken", "Beef", "Pork", "Lamb"]},
			{label: "Bakery", action: "/admin/order-bakery", products: ["Bread", "Croissant", "Baguette", "Pretzel"]},
		]
	},
	{
		title: "Kafka supplier", adapter: "outbound-kafka", forms: [
			{label: "Non-food", action: "/admin/order-nonfood", products: ["Detergent", "Soap", "Sponge", "Paper towels"]},
		]
	},
];

// Restocking a product from its inventory row reuses the order endpoint of its ProductType's supplier.
const RESTOCK_ACTIONS: Record<string, string> = {
	FRUIT: "/admin/order-fruits",
	VEGETABLE: "/admin/order-vegetables",
	DAIRY: "/admin/order-dairy",
	BEVERAGE: "/admin/order-beverages",
	MEAT: "/admin/order-meat",
	BAKERY: "/admin/order-bakery",
	NON_FOOD: "/admin/order-nonfood",
};

export const AdminPage = (vm: AdminPageVM): HtmlResult => html`
	<div class="columns">
		<div class="column is-half">
			<h2 class="title is-4">Restock Inventory
				<!-- Randomize only fills the forms: a random product, and either a random preset quantity or a custom one
				     of 80-600 (setting .checked/.value is invisible to hx-live, hence the refresh);
				     Submit all submits each one as if its Order button was clicked. -->
				<button class="button is-light is-small" type="button" id="admin-randomize-btn"
					hx-on:click="q('.order-form').forEach(f => {
						const pick = rs => rs[Math.floor(Math.random() * rs.length)];
						pick(f.querySelectorAll('input[name=productName]')).checked = true;
						const qty = pick(f.querySelectorAll('input[name=quantity]'));
						qty.checked = true;
						if (qty.matches('.qty-custom')) qty.value = f.querySelector('.qty-custom-input').value = 80 + Math.floor(Math.random() * 521);
					}); htmx.live.refresh()">Randomize (dev)</button>
				<button class="button is-link is-small" type="button" id="admin-submit-all-btn"
					hx-on:click="q('.order-form').requestSubmit()"
					hx-live="this.disabled = !q('.order-form').every(f => f.matches(':valid'))">Submit all</button>
			</h2>

			${SUPPLIER_BOXES.map(SupplierBox)}
		</div>

		<div class="column is-half">
			<h2 class="title is-4">Current Inventory</h2>
			<div id="admin-inventory" hx-get="/admin/inventory-fragment" hx-trigger="inventoryChanged from:body" hx-sync="this:replace" hx-swap="innerMorph">
				${AdminInventory({products: vm.products})}
			</div>

			${AuditPanel({auditEntries: vm.auditEntries})}
		</div>
	</div>
`;

// Re-fetched on every inventoryChanged event pushed by the shell's SSE stream (/inventory/events) and morphed
// into #admin-inventory: rows are matched by id, so new products appear and rows that stay keep typed
// restock quantities and focus. (A restock error in the last column is cleared by the next refresh.)
export const AdminInventory = (vm: AdminInventoryVM): HtmlResult => html`
	${vm.products.length === 0
		? html`<p class="has-text-grey"><em>No products in inventory yet.</em></p>`
		: html`
			<table class="table is-fullwidth is-striped">
				<thead>
				<tr><th>Name</th><th>Type</th><th>Available</th><th>Restock</th><th></th></tr>
				</thead>
				<tbody>
				${vm.products.map(InventoryRow)}
				</tbody>
			</table>`}
`;

// Name+type is the product key. Like the cart rows on /shop, the quantity input has no value attribute, so a
// morph never resets what was typed; it is kept after a restock, too, so the same amount can be ordered again.
const InventoryRow = (p: ProductRowVM): HtmlResult => html`
	<tr id="row-${p.name}-${p.type}">
		<td>${p.name}</td>
		<td>${p.type}</td>
		<td>${p.availableAmount}</td>
		<td>
			<form hx-post="${RESTOCK_ACTIONS[p.type]}" hx-target="next .restock-error" hx-swap="innerHTML" class="field has-addons restock-form">
				<input type="hidden" name="productName" value="${p.name}">
				<div class="control"><input class="input is-small" name="quantity" type="number" placeholder="Qty" min="1" max="2000" required style="width:80px"></div>
				<div class="control"><button class="button is-link is-small" type="submit" hx-live="this.disabled = !this.form.matches(':valid')">Restock</button></div>
			</form>
		</td>
		<td class="help is-danger restock-error"></td>
	</tr>
`;

const SupplierBox = (box: SupplierBox): HtmlResult => html`
	<div class="box">
		<h3 class="subtitle is-6">${box.title} <span class="tag is-light">${box.adapter}</span></h3>
		${box.forms.map(OrderFormRow)}
	</div>
`;

// A radio styled as a Bulma button: the native dot is hidden with is-sr-only (still focusable and clickable via the
// label) and hx-live toggles is-link on the label to follow its radio's checked state.
const RadioButton = (name: string, value: string, text: string): HtmlResult => html`
	<label class="button is-small mb-0" hx-live="class.toggle('is-link', q('input in this').checked)"><input type="radio" name="${name}" value="${value}" class="is-sr-only" required>${text}</label>
`;

// `method`/`action` stay for the e2e selectors (form[action=…]); htmx submits via hx-post.
// Both radio groups are required and nothing is preselected, so hx-live keeps Order disabled until a product and a
// quantity are chosen (matches(':valid') rather than checkValidity(), which would fire `invalid` events on every
// recompute). The custom quantity is the last `quantity` radio: the number input next to it has no name and only
// copies what is typed into that radio's value, so exactly one `quantity` is submitted. The input is readonly -
// and thereby exempt from validation - unless its radio is checked; focusing it checks the radio.
// min/max mirror the *Order records' @Min(1) @Max(2000). All of this is UX only - the server validates again.
const OrderFormRow = (f: OrderForm): HtmlResult => html`
	<form method="post" action="${f.action}" hx-post="${f.action}" hx-target="next .order-error" hx-swap="innerHTML" class="order-form">
		<div class="field is-horizontal mb-2">
			<div class="field-label"><label class="label">${f.label}</label></div>
			<div class="field-body">
				<div class="field">
					<div class="buttons has-addons mb-2">
						${f.products.map(p => RadioButton('productName', p, p))}
					</div>
					<div class="field is-grouped is-align-items-center">
						<div class="control buttons has-addons is-align-items-center mb-0">
							${PRESET_QUANTITIES.map(n => RadioButton('quantity', String(n), String(n)))}
							<label class="button is-small mb-0" hx-live="class.toggle('is-link', q('input in this').checked)"><input type="radio" name="quantity" value="" class="qty-custom is-sr-only" aria-label="Custom quantity">Other</label>
							<input class="input is-small qty-custom-input" type="number" placeholder="Qty" min="1" max="2000" required style="width:90px"
								aria-label="${f.label} custom quantity"
								hx-live="this.readOnly = !q('previous .qty-custom').checked"
								hx-on:focus="q('previous .qty-custom').checked = true"
								hx-on:input="q('previous .qty-custom').value = this.value">
						</div>
						<div class="control"><button class="button is-link is-small" type="submit" hx-live="this.disabled = !this.form.matches(':valid')">Order</button></div>
					</div>
				</div>
			</div>
		</div>
	</form>
	<p class="help is-danger order-error mb-3"></p>
`;

export const AuditPanel = (vm: AuditPanelVM): HtmlResult => html`
	<div id="audit-panel" hx-get="/admin/audit-fragment" hx-trigger="every 3s" hx-swap="outerHTML">
		<h2 class="title is-4">Audit Log</h2>
		${vm.auditEntries.length === 0
			? html`<p class="has-text-grey"><em>No audit log entries yet.</em></p>`
			: html`
				<div style="overflow-x:auto">
					<table class="table is-fullwidth is-narrow" style="white-space:nowrap">
						<thead>
						<tr><th>Time</th><th>Event</th><th>Details</th></tr>
						</thead>
						<tbody>
						${vm.auditEntries.map(AuditRow)}
						</tbody>
					</table>
				</div>`}
	</div>
`;

const AuditRow = (e: AuditEntryVM): HtmlResult => html`
	<tr>
		<td>${e.timestamp}</td>
		<td>${e.event}</td>
		<td>${e.details}</td>
	</tr>
`;

export const OrderErrors = (vm: OrderErrorsVM): HtmlResult =>
	html`${vm.messages.map((m, i) => i === 0 ? html`${m}` : html`<br>${m}`)}`;
