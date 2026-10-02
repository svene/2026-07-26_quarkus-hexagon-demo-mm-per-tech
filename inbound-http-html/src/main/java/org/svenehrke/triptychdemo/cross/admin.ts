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

type OrderForm = { label: string, action: string, defaultName: string };
type SupplierBox = { title: string, adapter: string, forms: OrderForm[] };

const SUPPLIER_BOXES: SupplierBox[] = [
	{
		title: "REST suppliers", adapter: "outbound-httpclient", forms: [
			{label: "Fruits", action: "/admin/order-fruits", defaultName: "Mango"},
			{label: "Vegetables", action: "/admin/order-vegetables", defaultName: "Carrot"},
			{label: "Dairy", action: "/admin/order-dairy", defaultName: "Milk"},
		]
	},
	{
		title: "SOAP suppliers", adapter: "outbound-webservice", forms: [
			{label: "Beverages", action: "/admin/order-beverages", defaultName: "Cola"},
			{label: "Meat", action: "/admin/order-meat", defaultName: "Chicken"},
			{label: "Bakery", action: "/admin/order-bakery", defaultName: "Bread"},
		]
	},
	{
		title: "Kafka supplier", adapter: "outbound-kafka", forms: [
			{label: "Non-food", action: "/admin/order-nonfood", defaultName: "Detergent"},
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
			<h2 class="title is-4">Current Inventory</h2>
			<div id="admin-inventory" hx-get="/admin/inventory-fragment" hx-trigger="inventoryChanged from:body" hx-sync="this:replace" hx-swap="innerMorph">
				${AdminInventory({products: vm.products})}
			</div>

			<h2 class="title is-4">Restock Inventory
				<!-- Randomize only fills the forms (setting .value is invisible to hx-live, hence the refresh);
				     Submit all submits each one as if its Order button was clicked. -->
				<button class="button is-light is-small" type="button" id="admin-randomize-btn"
					hx-on:click="q('.order-form').forEach(f => { f.productName.value = f.dataset.defaultName; f.quantity.value = 80 + Math.floor(Math.random() * 521) }); htmx.live.refresh()">Randomize (dev)</button>
				<button class="button is-link is-small" type="button" id="admin-submit-all-btn"
					hx-on:click="q('.order-form').requestSubmit()"
					:disabled="!q('.order-form').every(f => f.matches(':valid'))">Submit all</button>
			</h2>

			${SUPPLIER_BOXES.map(SupplierBox)}
		</div>

		<div class="column is-half">
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
				<div class="control"><button class="button is-link is-small" type="submit" :disabled="!this.form.matches(':valid')">Restock</button></div>
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

// `method`/`action` stay for the e2e selectors (form[action=…]); htmx submits via hx-post.
// min/max mirror the *Order records' @Min(1) @Max(2000); hx-live keeps Order disabled while the form is invalid
// (matches(':valid') rather than checkValidity(), which would fire `invalid` events on every recompute).
// Both are UX only - the server validates again.
const OrderFormRow = (f: OrderForm): HtmlResult => html`
	<form method="post" action="${f.action}" hx-post="${f.action}" hx-target="next .order-error" hx-swap="innerHTML" class="field has-addons order-form" data-default-name="${f.defaultName}">
		<div class="control"><span class="button is-static">${f.label}</span></div>
		<div class="control"><input class="input" name="productName" placeholder="e.g. ${f.defaultName}" required></div>
		<div class="control"><input class="input" name="quantity" type="number" placeholder="Qty" min="1" max="2000" required style="width:90px"></div>
		<div class="control"><button class="button is-link" type="submit" :disabled="!this.form.matches(':valid')">Order</button></div>
	</form>
	<p class="help is-danger order-error"></p>
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
