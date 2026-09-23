package org.svenehrke.triptychdemo.cross.purchase;

import jakarta.validation.ConstraintViolation;

import java.util.List;
import java.util.Set;
import java.util.SortedMap;

public sealed interface ParsedPurchase permits Purchase, ParsedPurchase.Invalid {

	/**
	 * Violations keyed by the (0-based) index of the offending item in the submitted list, so an error
	 * message can say which item failed.
	 */
	record Invalid(SortedMap<Integer, Set<ConstraintViolation<PurchaseItem>>> violationsByItemIndex) implements ParsedPurchase {

		/** One message per violation, prefixed with its item, e.g. {@code "items[1]: must not be blank"}. */
		public List<String> messages() {
			return violationsByItemIndex.entrySet().stream()
				.flatMap(e -> e.getValue().stream().map(v -> "items[" + e.getKey() + "]: " + v.getMessage()))
				.toList();
		}
	}
}
