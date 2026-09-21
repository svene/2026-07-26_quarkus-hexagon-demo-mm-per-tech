package org.svenehrke.triptychdemo.feature.fruit;

import java.util.List;

public sealed interface ParsedFruitDelivery {
	static ParsedFruitDelivery parse(String productName, int quantity) {
		// TODO: validate productName
		return FruitDelivery.isValid(quantity)
			? new ValidFruitDelivery(new FruitDelivery(productName, quantity))
			: new InvalidFruitDelivery(productName, quantity, List.of("invalid quantity: %d (valid range: ]0,%d])".formatted(quantity, FruitDelivery.MAX_QUANTITY)));
	}

	record ValidFruitDelivery(FruitDelivery fruitDelivery) implements  ParsedFruitDelivery {}
	record InvalidFruitDelivery(String productName, int quantity, List<String> errors) implements  ParsedFruitDelivery {}
}
