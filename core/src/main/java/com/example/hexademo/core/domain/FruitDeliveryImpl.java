package com.example.hexademo.core.domain;

// package-private — not visible outside this package;
// the only way to obtain an instance externally is FruitDelivery.parse(...)
record FruitDeliveryImpl(int quantity) implements FruitDelivery {

	private static final int MAX_QUANTITY = 10_000;

	FruitDeliveryImpl {
		if (!isValid(quantity)) {
			throw new IllegalArgumentException("quantity out parse range: " + quantity);
		}
	}

	static boolean isValid(int quantity) {
		return quantity > 0 && quantity <= MAX_QUANTITY;
	}
}
