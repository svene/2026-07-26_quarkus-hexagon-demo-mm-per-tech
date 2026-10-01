import {html} from "hono/html";
import type {ProductRowVM, ShopAvailabilityVM, ShopPageVM} from "./generated/vm-types";
import type {HtmlResult} from "./route-types";

// Delegated: the button is rendered into #app after the page has loaded.
// Fills 2-4 random rows with a quantity up to 10 (or the row's stock); the customer still clicks Purchase.
document.addEventListener("click", (event) => {
	if (!(event.target as Element).closest("#shop-randomize-btn")) return;
	const inputs = Array.from(document.querySelectorAll<HTMLInputElement>(".qty-input"));
	inputs.forEach(input => input.value = "");
	const count = Math.min(inputs.length, 2 + Math.floor(Math.random() * 3)); // 2-4
	const shuffled = inputs.slice().sort(() => Math.random() - 0.5);
	shuffled.slice(0, count).forEach(input => {
		const max = Math.min(parseInt(input.dataset.max!, 10) || 1, 10);
		input.value = String(1 + Math.floor(Math.random() * max));
	});
});

// Checkout re-renders the whole page into #app (with errors on 400/409, fresh after a purchase).
export const ShopPage = (vm: ShopPageVM): HtmlResult => html`
	${vm.errors.length === 0 ? '' : html`
		<div class="notification is-danger" id="checkout-errors">
			<p><strong>Purchase not processed:</strong></p>
			<ul>
				${vm.errors.map(e => html`<li>${e}</li>`)}
			</ul>
		</div>`}

	${vm.products.length === 0
		? html`<p class="has-text-grey"><em>No products available to purchase right now.</em></p>`
		: html`
			<div hx-get="/shop/inventory-fragment" hx-trigger="every 3s" hx-swap="none"></div>

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
					<div class="control"><button class="button is-link" type="submit">Purchase</button></div>
					<div class="control"><button class="button is-light" type="button" id="shop-randomize-btn">Randomize (dev)</button></div>
				</div>
			</form>`}
`;

const CartRow = (p: ProductRowVM): HtmlResult => html`
	<tr>
		<td>${p.name}</td>
		<td>${p.type}</td>
		<td id="avail-${p.name}">${p.availableAmount}</td>
		<td>
			<input type="hidden" name="productName" value="${p.name}">
			<input type="number" name="quantity" class="input qty-input" min="0" max="${p.availableAmount}" data-max="${p.availableAmount}" value="" style="width:100px">
		</td>
	</tr>
`;

// Partials update only the "Available" cells, so the poll never wipes a customer's typed quantities.
export const ShopAvailability = (vm: ShopAvailabilityVM): HtmlResult => html`
	${vm.products.map(p => html`<hx-partial id="avail-${p.name}" hx-swap="innerHTML">${p.availableAmount}</hx-partial>`)}
`;
