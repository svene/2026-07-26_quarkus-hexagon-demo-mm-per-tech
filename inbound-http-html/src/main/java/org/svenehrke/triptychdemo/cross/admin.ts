import {html} from "hono/html";
import type {
	AdminInventoryVM,
	AdminPageVM,
	AdminRequestsVM,
	AuditEntryVM,
	AuditPanelVM,
	LevelsVM,
	LocationVM,
	OrderErrorsVM,
	RequestVM,
	StockRowVM
} from "./generated/vm-types";
import type {HtmlResult} from "./route-types";
import {OriginTag, QuantityButtons} from "./location";

type OrderForm = { label: string, action: string, products: string[] };
type SupplierBox = { id: string, title: string, forms: OrderForm[] };

const PRESET_QUANTITIES = [10, 50, 100, 500];

const SUPPLIER_BOXES: SupplierBox[] = [
	{
		id: "rest", title: "REST suppliers", forms: [
			{label: "Fruits", action: "/admin/order-fruits", products: ["Mango", "Banana", "Apple", "Orange"]},
			{label: "Vegetables", action: "/admin/order-vegetables", products: ["Carrot", "Potato", "Tomato", "Cucumber"]},
			{label: "Dairy", action: "/admin/order-dairy", products: ["Milk", "Cheese", "Yogurt", "Butter"]},
		]
	},
	{
		id: "soap", title: "SOAP suppliers", forms: [
			{label: "Beverages", action: "/admin/order-beverages", products: ["Cola", "Water", "Juice", "Beer"]},
			{label: "Meat", action: "/admin/order-meat", products: ["Chicken", "Beef", "Pork", "Lamb"]},
			{label: "Bakery", action: "/admin/order-bakery", products: ["Bread", "Croissant", "Baguette", "Pretzel"]},
		]
	},
	{
		id: "kafka", title: "Kafka supplier", forms: [
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
	<!-- The supplier forms take only the width they need; the inventory side gets the rest and the audit log the full
	     width below both. -->
	<div class="columns">
		<div class="column is-narrow">
			<h2 class="title is-4">Restock Inventory</h2>
			<!-- One tab per supplier group. The panels stay in the DOM and are only hidden, so switching tabs keeps
			     what was chosen in a form; take() moves is-active to the clicked tab and hx-live follows it. -->
			<div class="box">
				<div class="tabs is-boxed is-small">
					<ul role="tablist">
						${SUPPLIER_BOXES.map((box, i) => SupplierTab(box, i === 0))}
					</ul>
				</div>
				${SUPPLIER_BOXES.map(SupplierPanel)}
			</div>
		</div>

		<div class="column">
			<h2 class="title is-4">Current Inventory</h2>
			<div id="admin-inventory" hx-get="/admin/inventory-fragment" hx-trigger="inventoryChanged from:body" hx-sync="this:replace" hx-swap="innerMorph">
				${AdminInventory({locations: vm.locations, products: vm.products})}
			</div>

			<h2 class="title is-4 mt-5">Pending Requests</h2>
			<div id="admin-requests" hx-get="/admin/requests-fragment" hx-trigger="inventoryChanged from:body" hx-sync="this:replace" hx-swap="innerMorph">
				${AdminRequests({requests: vm.pendingRequests})}
			</div>
		</div>
	</div>

	${AuditPanel({auditEntries: vm.auditEntries})}
`;

// The product × location matrix, DC column first. Re-fetched on every inventoryChanged event pushed by the shell's
// SSE stream (/inventory/events) and morphed into #admin-inventory: rows are matched by id, so new products appear
// and rows that stay keep focus. (A restock error in the last column is cleared by the
// next refresh.) Restocking orders from the supplier, so it always goes to the DC.
export const AdminInventory = (vm: AdminInventoryVM): HtmlResult => html`
	${vm.products.length === 0
		? html`<p class="has-text-grey"><em>No products in inventory yet.</em></p>`
		: html`
			<div style="overflow-x:auto">
				<table class="table is-fullwidth is-striped is-narrow" id="stock-matrix">
					<thead>
					<tr><th>Name</th><th>Type</th>${vm.locations.map(LocationHeader)}<th>Restock DC</th><th></th></tr>
					</thead>
					<tbody>
					${vm.products.map(InventoryRow)}
					</tbody>
				</table>
			</div>`}
`;

// Narrow enough that two-word names like "Store Basel" wrap.
const LocationHeader = (l: LocationVM): HtmlResult => html`<th class="has-text-right" data-location="${l.id}" style="max-width:5em">${l.name}</th>`;

// The cells to act on: an empty DC (order from the supplier) is red; a store / the online FC below its learned reorder
// point is orange (it requests from the DC automatically), its title shows the levels.
// (A location without a row for the product has no levels yet: there, only 0 counts as low.)
const AmountCell = (amount: number, levels: LevelsVM | null, i: number): HtmlResult => {
	const low = levels ? amount < levels.min : amount === 0;
	const lowClass = i === 0 ? 'has-text-danger has-text-weight-bold' : 'has-text-warning-dark has-text-weight-bold';
	return html`<td class="has-text-right ${low ? lowClass : ''}" title="${levels ? `min ${levels.min} / max ${levels.max}` : ''}">${amount}</td>`;
};

// Name+type is the product key. Each quantity button orders that amount right away.
const InventoryRow = (p: StockRowVM): HtmlResult => html`
	<tr id="row-${p.name}-${p.type}">
		<td>${p.name}</td>
		<td>${p.type}</td>
		${p.amounts.map((amount, i) => AmountCell(amount, p.levels[i], i))}
		<td>
			<form hx-post="${RESTOCK_ACTIONS[p.type]}" hx-target="next .restock-error" hx-swap="innerHTML" class="restock-form">
				<input type="hidden" name="productName" value="${p.name}">
				${QuantityButtons()}
			</form>
		</td>
		<td class="has-text-danger is-size-7 restock-error"></td>
	</tr>
`;

// Pending requests of all locations, oldest first; a delivery is shared among them in proportion to what each still
// needs. Fulfil sends what the DC has to this one right away, ahead of the others; the rest stays pending. Both answer with an empty 200 and the change event
// then refreshes this list, or with a 409 shown in the row's error cell.
export const AdminRequests = (vm: AdminRequestsVM): HtmlResult => html`
	${vm.requests.length === 0
		? html`<p class="has-text-grey"><em>No pending requests.</em></p>`
		: html`
			<table class="table is-fullwidth is-striped is-narrow" id="pending-requests">
				<thead>
				<tr><th>#</th><th>Location</th><th>Product</th><th class="has-text-right">Delivered</th><th>Origin</th><th></th><th></th></tr>
				</thead>
				<tbody>
				${vm.requests.map(PendingRequestRow)}
				</tbody>
			</table>`}
`;

const PendingRequestRow = (r: RequestVM): HtmlResult => html`
	<tr id="request-${r.id}">
		<td>${r.id}</td>
		<td>${r.locationName}<br><span class="is-size-7 has-text-grey" title="Requested at">${r.createdAt}</span></td>
		<td>${r.productName}</td>
		<td class="has-text-right">${r.delivered} / ${r.requested}</td>
		<td>${OriginTag(r)}</td>
		<td>
			<div class="buttons has-addons">
				<button class="button is-link is-small" type="button" hx-post="/admin/requests/${r.id}/fulfil" hx-target="next .request-error" hx-swap="innerHTML">Fulfil</button>
				<button class="button is-danger is-light is-small" type="button" hx-post="/admin/requests/${r.id}/reject" hx-target="next .request-error" hx-swap="innerHTML">Reject</button>
			</div>
		</td>
		<td class="has-text-danger is-size-7 request-error"></td>
	</tr>
`;

const SupplierTab = (box: SupplierBox, active: boolean): HtmlResult => html`
	<li id="supplier-tab-${box.id}" class="${active ? 'is-active' : ''}" hx-on:click="take('.is-active')">
		<a role="tab" aria-controls="supplier-panel-${box.id}" hx-live="this.ariaSelected = q('closest li').matches('.is-active')">${box.title}</a>
	</li>
`;

const SupplierPanel = (box: SupplierBox, i: number): HtmlResult => html`
	<div id="supplier-panel-${box.id}" role="tabpanel" ${i === 0 ? '' : 'hidden'}
		hx-live="this.hidden = !q('#supplier-tab-${box.id}').matches('.is-active')">
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
	<form method="post" action="${f.action}" hx-post="${f.action}" hx-target="next .order-error" hx-swap="innerHTML">
		<div class="field mb-2">
			<p class="label is-small mb-1">${f.label}</p>
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
