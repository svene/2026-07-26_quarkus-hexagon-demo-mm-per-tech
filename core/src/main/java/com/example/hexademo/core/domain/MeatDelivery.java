package com.example.hexademo.core.domain;

import java.util.Optional;

public record MeatDelivery(String productName, int quantity) {

	private static final int MAX_QUANTITY = 10_000;

	/**
	 * @deprecated Use {@link #parse(String, int)} instead, which returns an
	 * {@link Optional} rather than throwing on invalid input.
	 * Only intended to be used by deserialization tools like Jackson
	 */
	@Deprecated
	public MeatDelivery {
		if (!isValid(quantity)) {
			throw new IllegalArgumentException("quantity out parse range: " + quantity);
		}
	}

	private static boolean isValid(int quantity) {
		return quantity > 0 && quantity <= MAX_QUANTITY;
	}

	public static Optional<MeatDelivery> parse(String productName, int quantity) {
		// TODO: validate productName
		return isValid(quantity)
			? Optional.of(new MeatDelivery(productName, quantity))
			: Optional.empty();
	}
}
