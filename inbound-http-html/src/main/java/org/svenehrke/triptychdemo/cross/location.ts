import {html} from "hono/html";
import type {LocationInventoryVM, LocationProductRowVM, LocationsPageVM, PointVM, RequestVM, StoreMetricsVM, StoreOccupancyVM} from "./generated/vm-types";
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

// What the store's checkout system reports (door counters, tills), refreshed on each report. The app opens and closes
// the tills by these reports ("auto"); a closed till that is busy closes once its customer has paid. A full store
// turns new customers away (lost sales): more tills let them in. Below the line the same reports over time.
export const StoreOccupancy = (vm: StoreOccupancyVM): HtmlResult => {
	const r = vm.report;
	if (!r) return html`<p class="has-text-grey mb-4"><em>No occupancy reported yet.</em></p>`;
	return html`
		<div class="level is-mobile is-justify-content-flex-start mb-4 ${r.stale ? 'has-text-grey' : ''}">
			<div class="level-item is-flex-grow-0 mr-5"><span title="Customers inside / capacity"><strong class="occupancy-inside">${r.inside} / ${r.capacity}</strong> inside</span>${r.full ? html`<span class="tag is-danger ml-2">full</span>` : ''}</div>
			<div class="level-item is-flex-grow-0 mr-5"><span class="tag is-medium ${r.queuing > 0 ? 'is-warning' : 'is-light'}"><strong class="occupancy-queuing mr-1">${r.queuing}</strong> queuing for a till</span></div>
			<div class="level-item is-flex-grow-0 mr-5"><span title="Busy tills / open tills">tills <strong class="occupancy-tills">${r.tillsBusy} / ${r.tills}</strong> busy</span>${vm.autoTills ? html`<span class="tag is-info is-light ml-2" title="The app opens and closes the tills by the queue">auto</span>` : ''}</div>
			<div class="level-item is-flex-grow-0 mr-5"><span title="Customers who paid at a till in the last demo day (1 min): the throughput"><strong class="occupancy-paid">${r.paid}</strong> paid (last minute)</span></div>
			<div class="level-item is-flex-grow-0 mr-5"><span class="${r.turnedAway > 0 ? 'has-text-danger' : 'has-text-grey'}" title="Customers who found the store full in the last demo day (1 min): lost sales"><strong class="occupancy-turned-away">${r.turnedAway}</strong> turned away (last minute)</span></div>
			<div class="level-item is-flex-grow-0 is-size-7 has-text-grey" title="Time of the checkout system's latest report">${r.stale ? `as of ${r.measuredAt}, no newer report` : r.measuredAt}</div>
		</div>
		${StoreMetrics(vm.metrics)}
	`;
};

// The charts: plain SVG, x = seconds in the window, y scaled to the chart's height. preserveAspectRatio="none"
// stretches the drawing to the column's width; non-scaling-stroke (in theme.css) keeps the lines thin. So the SVG
// has no text: the legends and scales are HTML above it.
const CHART_HEIGHT = 100;
// Room above the maximum and below 0, so a line at either isn't half cut off by the edge.
const CHART_PAD = 4;
const MAX_TILLS = 8;
// The stub reports every 5 s; a longer gap (it stopped) isn't shaded as full.
const MAX_SHADE_SECONDS = 10;

const StoreMetrics = (m: StoreMetricsVM): HtmlResult => {
	if (m.points.length === 0) return html``;
	const minutes = m.windowSeconds / 60;
	const occupancyMax = Math.max(1, ...m.points.map(p => Math.max(p.capacity, p.inside)));
	const flowMax = Math.max(1, ...m.points.map(p => Math.max(p.queuing, p.paid, p.turnedAway)));
	return html`
		<div class="columns mb-4 store-metrics">
			<div class="column">
				<p class="is-size-7 mb-1">
					<strong>Occupancy</strong>, last ${minutes} min ·
					<span class="${m.fullPercent > 0 ? 'has-text-danger' : 'has-text-grey'}" title="Share of the reports that found the store full"><strong class="metrics-full">${m.fullPercent} %</strong> of the time full</span>
				</p>
				<p class="is-size-7 has-text-grey mb-1">
					<span class="chart-key chart-inside"></span>inside
					<span class="chart-key chart-capacity ml-3"></span>capacity
					<span class="chart-key chart-full ml-3"></span>full · scale 0–${occupancyMax}
				</p>
				<svg class="metrics-chart" viewBox="0 0 ${m.windowSeconds} ${CHART_HEIGHT}" preserveAspectRatio="none" role="img" aria-label="Customers inside vs. capacity">
					${fullPeriods(m)}
					<polyline class="chart-capacity" points="${line(m.points, p => p.capacity, occupancyMax)}"/>
					<polyline class="chart-inside" points="${line(m.points, p => p.inside, occupancyMax)}"/>
				</svg>
			</div>
			<div class="column">
				<p class="is-size-7 mb-1">
					<strong>Tills</strong>, last ${minutes} min ·
					Ø <strong class="metrics-avg-paid">${m.avgPaid.toFixed(1)}</strong> paid /
					<span class="${m.avgTurnedAway > 0 ? 'has-text-danger' : 'has-text-grey'}">Ø <strong class="metrics-avg-turned-away">${m.avgTurnedAway.toFixed(1)}</strong> turned away</span> per minute
				</p>
				<p class="is-size-7 has-text-grey mb-1">
					<span class="chart-key chart-queuing"></span>queuing
					<span class="chart-key chart-paid ml-3"></span>paid / min
					<span class="chart-key chart-turned-away ml-3"></span>turned away / min · scale 0–${flowMax}
					<span class="chart-key chart-tills ml-3"></span>open tills (scale 0–${MAX_TILLS})
				</p>
				<svg class="metrics-chart" viewBox="0 0 ${m.windowSeconds} ${CHART_HEIGHT}" preserveAspectRatio="none" role="img" aria-label="Queue, throughput, turned away and open tills">
					<polyline class="chart-tills" points="${steps(m.points, p => p.tills, MAX_TILLS)}"/>
					<polyline class="chart-turned-away" points="${line(m.points, p => p.turnedAway, flowMax)}"/>
					<polyline class="chart-queuing" points="${line(m.points, p => p.queuing, flowMax)}"/>
					<polyline class="chart-paid" points="${line(m.points, p => p.paid, flowMax)}"/>
				</svg>
			</div>
		</div>
	`;
};

const y = (value: number, max: number): number =>
	Math.round((CHART_HEIGHT - CHART_PAD - value * (CHART_HEIGHT - 2 * CHART_PAD) / max) * 10) / 10;

const line = (points: PointVM[], value: (p: PointVM) => number, max: number): string =>
	points.map(p => `${p.t},${y(value(p), max)}`).join(' ');

// Holds each value until the next report: the tills change in steps, not gradually.
const steps = (points: PointVM[], value: (p: PointVM) => number, max: number): string =>
	points.flatMap((p, i) => {
		const here = `${p.t},${y(value(p), max)}`;
		return i === 0 ? [here] : [`${p.t},${y(value(points[i - 1]), max)}`, here];
	}).join(' ');

// A full report shades the time until the next one, at most MAX_SHADE_SECONDS.
const fullPeriods = (m: StoreMetricsVM): HtmlResult[] =>
	m.points.flatMap((p, i) => {
		if (p.inside < p.capacity) return [];
		const next = i + 1 < m.points.length ? m.points[i + 1].t : m.windowSeconds;
		const width = Math.max(0, Math.min(next, p.t + MAX_SHADE_SECONDS, m.windowSeconds) - p.t);
		return [html`<rect class="chart-full" x="${p.t}" y="0" width="${width}" height="${CHART_HEIGHT}"/>`];
	});

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
