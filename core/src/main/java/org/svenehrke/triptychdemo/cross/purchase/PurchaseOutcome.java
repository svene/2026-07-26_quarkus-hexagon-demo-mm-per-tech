package org.svenehrke.triptychdemo.cross.purchase;

import org.svenehrke.triptychdemo.cross.inventory.Shortage;

import java.util.List;

/** Result of {@link PurchaseAPI#checkout}. A business outcome, not validation: the purchase itself is valid. */
public sealed interface PurchaseOutcome {

	record Completed() implements PurchaseOutcome {}

	/** Not enough stock for at least one item; nothing was deducted. */
	record Rejected(List<Shortage> shortages) implements PurchaseOutcome {

		/** One message per shortage, e.g. {@code "Apple: only 3 in stock (requested 5)"}. */
		public List<String> messages() {
			return shortages.stream().map(Shortage::message).toList();
		}
	}
}
