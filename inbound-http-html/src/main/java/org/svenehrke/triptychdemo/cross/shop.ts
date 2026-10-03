import {html} from "hono/html";
import type {ProductRowVM, ShopPageVM, ShopProductsVM} from "./generated/vm-types";
import type {HtmlResult} from "./route-types";

// Checkout re-renders the whole page into #app (with errors on 400/409, fresh after a purchase).
export const ShopPage = (vm: ShopPageVM): HtmlResult => html`
	${vm.errors.length === 0 ? '' : html`
		<div class="notification is-danger" id="checkout-errors">
			<p><strong>Purchase not processed:</strong></p>
			<ul>
				${vm.errors.map(e => html`<li>${e}</li>`)}
			</ul>
		</div>`}

	<div id="shop-products" hx-get="/shop/inventory-fragment" hx-trigger="inventoryChanged from:body" hx-sync="this:replace" hx-swap="innerMorph">
		${ShopProducts({products: vm.products})}
	</div>
`;

// Re-fetched on every inventoryChanged event pushed by the shell's SSE stream (/inventory/events) and morphed into
// #shop-products: rows are matched by id, so new products appear, sold-out ones
// disappear, and rows that stay keep the customer's typed quantities and focus.
export const ShopProducts = (vm: ShopProductsVM): HtmlResult => html`
	${vm.products.length === 0
		? html`<p class="has-text-grey"><em>No products available to purchase right now.</em></p>`
		: html`
			<form method="post" action="/shop/checkout" id="shop-form" hx-post="/shop/checkout" hx-target="#app" hx-swap="innerHTML">
				<table class="table is-fullwidth is-striped">
					<thead>
					<tr><th>Name</th><th>Type</th><th>Available</th><th>Qty to buy</th></tr>
					</thead>
					<tbody>
					${vm.products.map(CartRow)}
					</tbody>
				</table>
				<div class="field is-grouped">
					<div class="control"><button class="button is-link" type="submit" id="shop-purchase-btn"
						hx-live="this.disabled = !this.form.matches(':valid') || !q('.qty-input').some(i => i.valueAsNumber > 0)">Purchase</button></div>
					<!-- Randomize fills 2-4 random rows with a quantity up to 10 (or the row's stock); the customer still clicks
					     Purchase. Setting .value is invisible to hx-live, hence the refresh. -->
					<div class="control"><button class="button is-light" type="button" id="shop-randomize-btn"
						hx-on:click="q('.qty-input').value = '';
							q('.qty-input').arr().sort(() => Math.random() - 0.5).slice(0, 2 + Math.floor(Math.random() * 3))
								.forEach(i => i.value = 1 + Math.floor(Math.random() * Math.min(+i.max, 10)));
							htmx.live.refresh()">Randomize (dev)</button></div>
					<div class="control"><span class="button is-static" id="shop-cart-summary"
						hx-live="this.textContent = q('.qty-input').filter(i => i.valueAsNumber > 0).length + ' products, ' + q('.qty-input').reduce((n, i) => n + (i.valueAsNumber || 0), 0) + ' items'"></span></div>
				</div>
			</form>`}
`;

// Name+type is the product key. The quantity input deliberately has no value attribute: a morph overwrites a
// typed value only when the new markup's value attribute differs. `max` is the current stock; the morph updates it
// when stock changes, and hx-live re-flags the input via :valid (also catches negatives and fractions; matches()
// rather than checkValidity(), which would fire `invalid` events on every recompute). UX only - the server re-checks.
const CartRow = (p: ProductRowVM): HtmlResult => html`
	<tr id="row-${p.name}-${p.type}">
		<td>${p.name}</td>
		<td>${p.type}</td>
		<td>${p.availableAmount}</td>
		<td>
			<input type="hidden" name="productName" value="${p.name}">
			<input type="number" name="quantity" class="input qty-input" min="0" max="${p.availableAmount}" style="width:100px"
				hx-live="class.toggle('is-danger', !this.matches(':valid'))">
		</td>
	</tr>
`;
