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

// Delegated: the buttons are rendered into #app after the page has loaded.
// Randomize only fills the forms; Submit all submits each one as if its Order button was clicked.
document.addEventListener("click", (event) => {
	const target = event.target as Element;
	const forms = Array.from(document.querySelectorAll<HTMLFormElement>(".order-form"));
	if (target.closest("#admin-randomize-btn")) {
		forms.forEach(form => {
			form.querySelector<HTMLInputElement>('input[name="productName"]')!.value = form.dataset.defaultName!;
			form.querySelector<HTMLInputElement>('input[name="quantity"]')!.value = String(80 + Math.floor(Math.random() * 521)); // 80-600
		});
		// Setting .value fires no event; tell hx-live to recompute the Order / Submit all buttons.
		document.dispatchEvent(new Event("input"));
	} else if (target.closest("#admin-submit-all-btn")) {
		forms.forEach(form => form.requestSubmit());
	}
});

export const AdminPage = (vm: AdminPageVM): HtmlResult => html`
	<div class="columns">
		<div class="column is-half">
			<h2 class="title is-4">Current Inventory</h2>
			${vm.products.length === 0
				? html`<p class="has-text-grey"><em>No products in inventory yet.</em></p>`
				: html`
					<table class="table is-fullwidth is-striped">
						<thead>
						<tr><th>Name</th><th>Type</th><th>Available</th></tr>
						</thead>
						${AdminInventory({products: vm.products})}
					</table>`}

			<h2 class="title is-4">Restock Inventory
				<button class="button is-light is-small" type="button" id="admin-randomize-btn">Randomize (dev)</button>
				<button class="button is-link is-small" type="button" id="admin-submit-all-btn"
					:disabled="!q('.order-form').every(f => f.checkValidity())">Submit all</button>
			</h2>

			${SUPPLIER_BOXES.map(SupplierBox)}
		</div>

		<div class="column is-half">
			${AuditPanel({auditEntries: vm.auditEntries})}
		</div>
	</div>
`;

// Polls itself and is replaced wholesale (outerHTML) - it contains no user input.
export const AdminInventory = (vm: AdminInventoryVM): HtmlResult => html`
	<tbody id="inventory-body" hx-get="/admin/inventory-fragment" hx-trigger="every 3s" hx-swap="outerHTML">
		${vm.products.map(InventoryRow)}
	</tbody>
`;

const InventoryRow = (p: ProductRowVM): HtmlResult => html`
	<tr>
		<td>${p.name}</td>
		<td>${p.type}</td>
		<td>${p.availableAmount}</td>
	</tr>
`;

const SupplierBox = (box: SupplierBox): HtmlResult => html`
	<div class="box">
		<h3 class="subtitle is-6">${box.title} <span class="tag is-light">${box.adapter}</span></h3>
		${box.forms.map(OrderFormRow)}
	</div>
`;

// `method`/`action` stay for the e2e selectors (form[action=…]); htmx submits via hx-post.
// min/max mirror the *Order records' @Min(1) @Max(2000); hx-live keeps Order disabled while the form is invalid.
// Both are UX only - the server validates again.
const OrderFormRow = (f: OrderForm): HtmlResult => html`
	<form method="post" action="${f.action}" hx-post="${f.action}" hx-target="next .order-error" hx-swap="innerHTML" class="field has-addons order-form" data-default-name="${f.defaultName}">
		<div class="control"><span class="button is-static">${f.label}</span></div>
		<div class="control"><input class="input" name="productName" placeholder="e.g. ${f.defaultName}" required></div>
		<div class="control"><input class="input" name="quantity" type="number" placeholder="Qty" min="1" max="2000" required style="width:90px"></div>
		<div class="control"><button class="button is-link" type="submit" :disabled="!this.form.checkValidity()">Order</button></div>
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
