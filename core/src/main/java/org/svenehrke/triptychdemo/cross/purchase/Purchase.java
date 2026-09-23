package org.svenehrke.triptychdemo.cross.purchase;

import jakarta.validation.ConstraintViolation;

import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A purchase of several items, all-or-nothing: {@link #parse(List)} only yields a {@code Purchase} if
 * every item is valid. Since every {@link PurchaseItem} is valid by construction, so is every
 * {@code Purchase} - there are no constraints of its own to check.
 */
public record Purchase(List<PurchaseItem> items) implements ParsedPurchase {

	public Purchase {
		items = List.copyOf(items);
	}

	public static ParsedPurchase parse(List<ParsedPurchaseItem> parsedItems) {
		SortedMap<Integer, Set<ConstraintViolation<PurchaseItem>>> violationsByItemIndex = new TreeMap<>();
		for (int i = 0; i < parsedItems.size(); i++) {
			if (parsedItems.get(i) instanceof ParsedPurchaseItem.Invalid invalid) {
				violationsByItemIndex.put(i, invalid.violations());
			}
		}
		if (!violationsByItemIndex.isEmpty()) {
			return new ParsedPurchase.Invalid(violationsByItemIndex);
		}
		return new Purchase(parsedItems.stream().map(PurchaseItem.class::cast).toList());
	}

}
