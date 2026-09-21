package org.svenehrke.triptychdemo.feature.fruit;

import java.util.Optional;

public record FruitDelivery(String productName, int quantity) {

	static final int MAX_QUANTITY = 10_000;

	/**
	 * @deprecated Use {@link #parse(int)} instead, which returns an
	 * {@link Optional} rather than throwing on invalid input.
	 * Only intended to be used by deserialization tools like Jackson
	 */
	@Deprecated
	public FruitDelivery {
		if (!isValid(quantity)) {
			throw new IllegalArgumentException("quantity out parse range: " + quantity);
		}
	}

	static boolean isValid(int quantity) {
		return quantity > 0 && quantity <= MAX_QUANTITY;
	}

}
