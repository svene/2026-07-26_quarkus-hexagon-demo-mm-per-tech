package org.svenehrke.triptychdemo.cross.purchasing;

import org.svenehrke.triptychdemo.cross.products.ProductType;

import java.util.List;
import java.util.Optional;

/**
 * The DC's orders from suppliers, and the deliveries that close them. Every method is one transaction that locks the
 * product's DC stock row first - the lock order of {@code ReplenishmentRepositorySPI} - so the inventory position
 * {@link #openIfLow} computes cannot change underneath it.
 */
public interface SupplierOrderRepositorySPI {

	/**
	 * Records an order before it is sent: the supplier may deliver before sending even returns, and the delivery must
	 * find the order.
	 */
	SupplierOrder open(String productName, ProductType type, int quantity, SupplierOrderOrigin origin);

	/**
	 * Automatic ordering: if the DC's inventory position of the product (available + outstanding supplier orders −
	 * outstanding requests of the locations, i.e. the DC's backorders) is below its learned reorder point, records an
	 * {@link SupplierOrderOrigin#AUTOMATIC} order up to the order-up-to level (at most {@link SupplierOrder#MAX_QUANTITY}).
	 * Checking and recording happen in one locked transaction, so concurrent checks cannot both order. Empty if nothing
	 * was ordered, or the DC has never carried the product.
	 */
	Optional<SupplierOrder> openIfLow(String productName);

	/** Sending the order failed. Empty if there is no such open order. */
	Optional<SupplierOrder> cancel(long id);

	/**
	 * A delivery: adds it to the DC stock and closes the open orders of the product oldest first (a partly delivered one
	 * stays open), in one transaction. What is more than the open orders (or nobody ordered) is simply added. Returns
	 * every order the delivery went to.
	 */
	List<SupplierOrder> receiveDelivery(String productName, ProductType type, int quantity);

	/** Open orders, oldest first. */
	List<SupplierOrder> findOpen();
}
