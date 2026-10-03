import {html} from "hono/html";
import type {LocationInventoryVM, LocationProductRowVM, LocationsPageVM, RequestVM} from "./generated/vm-types";
import type {HtmlResult} from "./route-types";

// One section per store / the online FC, each refreshed on its own; the section id makes /locations#bern a link to Bern.
export const LocationsPage = (vm: LocationsPageVM): HtmlResult => html`
	${vm.locations.map(LocationSection)}
`;

const LocationSection = (vm: LocationInventoryVM): HtmlResult => html`
	<section class="block mb-6" id="location-${vm.locationId}">
		<h2 class="title is-3">${vm.locationName}</h2>
		<div hx-get="/locations/${vm.locationId}/inventory-fragment" hx-trigger="inventoryChanged from:body" hx-sync="this:replace" hx-swap="innerMorph">
			${LocationInventory(vm)}
		</div>
	</section>
`;

// Stock and requests in one fragment: a request changes both (or only the request list, if it has to wait), and
// every request publishes an inventoryChanged event. Re-fetched on that event and morphed, so rows that stay keep
// typed quantities and focus. All ids carry the location id: the page shows every location.
export const LocationInventory = (vm: LocationInventoryVM): HtmlResult => html`
	<div class="columns">
		<div class="column is-three-fifths">
			<h3 class="title is-5">Stock</h3>
			${vm.products.length === 0
				? html`<p class="has-text-grey"><em>The DC carries no products yet.</em></p>`
				: html`
					<table class="table is-fullwidth is-striped is-narrow" id="stock-${vm.locationId}">
						<thead>
						<tr><th>Name</th><th>Type</th><th class="has-text-right">Available</th><th class="has-text-right" title="Learned demand per period">Avg</th><th class="has-text-right" title="Reorder point: below it, stock is requested from the DC automatically">Min</th><th class="has-text-right" title="Order-up-to level: how far an automatic request fills up">Max</th><th class="has-text-right">DC</th><th>Request from DC</th><th></th></tr>
						</thead>
						<tbody>
						${vm.products.map(p => StockRow(vm.locationId, p))}
						</tbody>
					</table>`}
		</div>
		<div class="column">
			<h3 class="title is-5">Requests</h3>
			${vm.requests.length === 0
				? html`<p class="has-text-grey"><em>No requests yet.</em></p>`
				: html`
					<table class="table is-fullwidth is-striped is-narrow" id="requests-${vm.locationId}">
						<thead>
						<tr><th>#</th><th>Product</th><th class="has-text-right">Delivered</th><th>Status</th><th>Origin</th><th>Requested at</th></tr>
						</thead>
						<tbody>
						${vm.requests.map(RequestRow)}
						</tbody>
					</table>`}
		</div>
	</div>
`;

// Available against the learned levels: red below min (an automatic request is due), grey above max (overstocked).
// Without levels (no row for the product yet), only an empty shelf is red.
const availableClass = (p: LocationProductRowVM): string => {
	const belowMin = p.levels ? p.availableAmount < p.levels.min : p.availableAmount === 0;
	if (belowMin) return 'has-text-danger has-text-weight-bold';
	return p.levels && p.availableAmount > p.levels.max ? 'has-text-grey' : '';
};

// Name+type is the product key. As on /admin, the quantity input has no value attribute, so a morph never resets what
// was typed. min/max mirror StockRequest's @Min(1) @Max(2000) - UX only, the server validates again. Avg/Min/Max
// are learned (ReorderPolicyHandler) and read-only.
const StockRow = (locationId: string, p: LocationProductRowVM): HtmlResult => html`
	<tr id="row-${locationId}-${p.name}-${p.type}">
		<td>${p.name}</td>
		<td>${p.type}</td>
		<td class="has-text-right ${availableClass(p)}">${p.availableAmount}</td>
		<td class="has-text-right has-text-grey">${p.avgDemand == null ? '–' : p.avgDemand.toFixed(1)}</td>
		<td class="has-text-right has-text-grey">${p.levels ? p.levels.min : '–'}</td>
		<td class="has-text-right has-text-grey">${p.levels ? p.levels.max : '–'}</td>
		<td class="has-text-right has-text-grey">${p.dcAvailableAmount}</td>
		<td>
			<form hx-post="/locations/${locationId}/requests" hx-target="next .request-error" hx-swap="innerHTML" class="field has-addons request-form">
				<input type="hidden" name="productName" value="${p.name}">
				<div class="control"><input class="input is-small" name="quantity" type="number" placeholder="Qty" min="1" max="2000" required style="width:80px"></div>
				<div class="control"><button class="button is-link is-small" type="submit" hx-live="this.disabled = !this.form.matches(':valid')">Request</button></div>
			</form>
		</td>
		<td class="has-text-danger is-size-7 request-error"></td>
	</tr>
`;

const STATUS_TAGS: Record<string, string> = {PENDING: "is-warning", FULFILLED: "is-success", REJECTED: "is-danger"};

export const OriginTag = (r: RequestVM): HtmlResult =>
	r.origin === 'AUTOMATIC' ? html`<span class="tag is-info is-light">auto</span>` : html`<span class="has-text-grey">manual</span>`;

const RequestRow = (r: RequestVM): HtmlResult => html`
	<tr id="request-${r.id}">
		<td>${r.id}</td>
		<td>${r.productName}</td>
		<td class="has-text-right">${r.delivered} / ${r.requested}</td>
		<td><span class="tag ${STATUS_TAGS[r.status] ?? ''}">${r.status}</span></td>
		<td>${OriginTag(r)}</td>
		<td>${r.createdAt}</td>
	</tr>
`;
