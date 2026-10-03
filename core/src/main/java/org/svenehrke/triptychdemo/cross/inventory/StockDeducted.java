package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.cross.location.Replenished;

import java.util.Set;

/** A sale (online checkout or cashpoint) took stock of {@code productNames} from {@code location}. */
public record StockDeducted(Replenished location, Set<String> productNames) implements InventoryEvent {

	public StockDeducted {
		productNames = Set.copyOf(productNames);
	}
}
