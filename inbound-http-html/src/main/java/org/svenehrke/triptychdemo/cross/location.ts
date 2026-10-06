import {html} from "hono/html";
import type {LocationInventoryVM, LocationProductRowVM, LocationsPageVM, RequestVM, StoreOccupancyVM} from "./generated/vm-types";
import type {HtmlResult} from "./route-types";

// One section per store / the online FC, each refreshed on its own; the section id makes /locations#bern a link to Bern.
export const LocationsPage = (vm: LocationsPageVM): HtmlResult => html`
	${vm.locations.map(l => LocationSection(l, vm.occupancy.find(o => o.storeId === l.locationId)))}
`;

// The online FC has no occupancy (no customers inside).
const LocationSection = (vm: LocationInventoryVM, occupancy: StoreOccupancyVM | undefined): HtmlResult => html`
	<section class="block mb-6" id="location-${vm.locationId}">
		<h2 class="title is-3">${vm.locationName}</h2>
		${occupancy ? html`
			<div id="occupancy-${vm.locationId}" hx-get="/locations/${vm.locationId}/occupancy-fragment" hx-trigger="occupancyChanged-${vm.locationId} from:body" hx-sync="this:replace" hx-swap="innerMorph">
				${StoreOccupancy(occupancy)}
			</div>` : ''}
		<div hx-get="/locations/${vm.locationId}/inventory-fragment" hx-trigger="inventoryChanged from:body" hx-sync="this:replace" hx-swap="innerMorph">
			${LocationInventory(vm)}
		</div>
	</section>
`;

// Stock and requests in one fragment: a request changes both (or only the request list, if it has to wait), and
// every request publishes an inventoryChanged event. Re-fetched on that event and morphed, so rows that stay keep
// focus. All ids carry the location id: the page shows every location.
export const LocationInventory = (vm: LocationInventoryVM): HtmlResult => html`
	<div class="columns">
		<div class="column is-three-fifths">
			<h3 class="title is-5">Stock</h3>
			${vm.products.length === 0
				? html`<p class="has-text-grey"><em>The DC carries no products yet.</em></p>`
				: html`
					<table class="table is-fullwidth is-striped is-narrow" id="stock-${vm.locationId}">
						<thead>
						<tr><th>Name</th><th>Type</th><th class="has-text-right">Available</th><th class="has-text-right" title="Shipped by the DC, not arrived yet">In transit</th><th class="has-text-right" title="Learned demand per period">Avg</th><th class="has-text-right" title="Reorder point: below it, stock is requested from the DC automatically">Min</th><th class="has-text-right" title="Order-up-to level: how far an automatic request fills up">Max</th><th class="has-text-right">DC</th><th>Request from DC</th><th></th></tr>
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
						<tr><th>#</th><th>Product</th><th class="has-text-right" title="Shipped by the DC (it may still be in transit) / requested">Shipped</th><th>Status</th><th>Origin</th></tr>
						</thead>
						<tbody>
						${vm.requests.map(RequestRow)}
						</tbody>
					</table>`}
		</div>
	</div>
`;

// What the store's checkout system reports (door counters, tills), refreshed on each report. − / + send the reported
// number of tills ± 1; the checkout system reports again right after the change. A closed till that is busy closes
// once its customer has paid. A full store turns new customers away (lost sales): more tills let them in.
export const StoreOccupancy = (vm: StoreOccupancyVM): HtmlResult => {
	const r = vm.report;
	if (!r) return html`<p class="has-text-grey mb-4"><em>No occupancy reported yet.</em></p>`;
	return html`
		<div class="level is-mobile is-justify-content-flex-start mb-4 ${r.stale ? 'has-text-grey' : ''}">
			<div class="level-item is-flex-grow-0 mr-5"><span title="Customers inside / capacity"><strong class="occupancy-inside">${r.inside} / ${r.capacity}</strong> inside</span>${r.full ? html`<span class="tag is-danger ml-2">full</span>` : ''}</div>
			<div class="level-item is-flex-grow-0 mr-5"><span class="tag is-medium ${r.queuing > 0 ? 'is-warning' : 'is-light'}"><strong class="occupancy-queuing mr-1">${r.queuing}</strong> queuing for a till</span></div>
			<div class="level-item is-flex-grow-0 mr-3"><span title="Busy tills / open tills">tills <strong class="occupancy-tills">${r.tillsBusy} / ${r.tills}</strong> busy</span></div>
			<form class="level-item is-flex-grow-0 mr-3" hx-post="/locations/${vm.storeId}/tills" hx-target="next .tills-error" hx-swap="innerHTML">
				<div class="buttons has-addons are-small mb-0">
					<button class="button mb-0" type="submit" name="tills" value="${r.tills - 1}" title="Close a till" aria-label="Close a till" ${r.tills <= 1 ? 'disabled' : ''}>−</button>
					<button class="button mb-0" type="submit" name="tills" value="${r.tills + 1}" title="Open a till" aria-label="Open a till" ${r.tills >= vm.maxTills ? 'disabled' : ''}>+</button>
				</div>
			</form>
			<div class="level-item is-flex-grow-0 mr-5"><span class="${r.turnedAway > 0 ? 'has-text-danger' : 'has-text-grey'}" title="Customers who found the store full in the last demo day (1 min): lost sales"><strong class="occupancy-turned-away">${r.turnedAway}</strong> turned away (last minute)</span></div>
			<div class="level-item is-flex-grow-0 has-text-danger is-size-7 tills-error"></div>
			<div class="level-item is-flex-grow-0 is-size-7 has-text-grey" title="Time of the checkout system's latest report">${r.stale ? `as of ${r.measuredAt}, no newer report` : r.measuredAt}</div>
		</div>
	`;
};

// Available against the learned levels: red below min (an automatic request is due), grey above max (overstocked).
// Without levels (no row for the product yet), only an empty shelf is red.
const availableClass = (p: LocationProductRowVM): string => {
	const belowMin = p.levels ? p.availableAmount < p.levels.min : p.availableAmount === 0;
	if (belowMin) return 'has-text-danger has-text-weight-bold';
	return p.levels && p.availableAmount > p.levels.max ? 'has-text-grey' : '';
};

// Name+type is the product key. Each quantity button requests that amount from the DC right away; what the DC ships
// is in transit until the carrier reports its arrival. Avg/Min/Max are learned (ReorderPolicyHandler) and read-only.
const StockRow = (locationId: string, p: LocationProductRowVM): HtmlResult => html`
	<tr id="row-${locationId}-${p.name}-${p.type}">
		<td>${p.name}</td>
		<td>${p.type}</td>
		<td class="has-text-right ${availableClass(p)}">${p.availableAmount}</td>
		<td class="has-text-right has-text-grey">${p.inTransit}</td>
		<td class="has-text-right has-text-grey">${p.avgDemand == null ? '–' : p.avgDemand.toFixed(1)}</td>
		<td class="has-text-right has-text-grey">${p.levels ? p.levels.min : '–'}</td>
		<td class="has-text-right has-text-grey">${p.levels ? p.levels.max : '–'}</td>
		<td class="has-text-right has-text-grey">${p.dcAvailableAmount}</td>
		<td>
			<form hx-post="/locations/${locationId}/requests" hx-target="next .request-error" hx-swap="innerHTML" class="request-form">
				<input type="hidden" name="productName" value="${p.name}">
				${QuantityButtons()}
			</form>
		</td>
		<td class="has-text-danger is-size-7 request-error"></td>
	</tr>
`;

const QUANTITIES = [10, 20, 50, 100];

// One submit button per quantity, for a form that carries the productName: htmx adds the clicked button's
// name/value, so a click submits that quantity right away. Used by the restock column on /admin, too.
export const QuantityButtons = (): HtmlResult => html`
	<div class="buttons has-addons are-small is-flex-wrap-nowrap mb-0">
		${QUANTITIES.map(n => html`<button class="button is-link mb-0" type="submit" name="quantity" value="${n}">${n}</button>`)}
	</div>
`;

const STATUS_TAGS: Record<string, string> = {PENDING: "is-warning", FULFILLED: "is-success", REJECTED: "is-danger"};

// Also used for supplier orders, which have the same two origins.
// SEED only occurs for supplier orders (seeding the DC).
export const OriginTag = (r: {origin: string}): HtmlResult =>
	r.origin === 'AUTOMATIC' ? html`<span class="tag is-info is-light">auto</span>`
		: r.origin === 'SEED' ? html`<span class="tag is-success is-light">seed</span>`
		: html`<span class="has-text-grey">manual</span>`;

const RequestRow = (r: RequestVM): HtmlResult => html`
	<tr id="request-${r.id}">
		<td>${r.id}</td>
		<td>${r.productName}<br><span class="is-size-7 has-text-grey" title="Requested at">${r.createdAt}</span></td>
		<td class="has-text-right">${r.shipped} / ${r.requested}</td>
		<td><span class="tag ${STATUS_TAGS[r.status] ?? ''}">${r.status}</span></td>
		<td>${OriginTag(r)}</td>
	</tr>
`;
