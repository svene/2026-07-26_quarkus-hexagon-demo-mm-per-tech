package org.svenehrke.triptychdemo.feature.fruit;

import java.util.List;
import java.util.Optional;

public record FruitDelivery(String productName, int quantity) implements ParsedFruitDelivery {

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

	public static ParsedFruitDelivery parse(String productName, int quantity) {
		// TODO: validate productName
		return isValid(quantity)
			? new FruitDelivery(productName, quantity)
			: new ParsedFruitDelivery.Invalid(List.of(
				"%s: invalid quantity %d (valid range: ]0,%d])".formatted(productName, quantity, MAX_QUANTITY)));
	}

}
