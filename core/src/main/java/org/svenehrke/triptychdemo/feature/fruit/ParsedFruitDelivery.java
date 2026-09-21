package org.svenehrke.triptychdemo.feature.fruit;

import java.util.List;

/**
 * No separate "Valid" wrapper is needed: {@link FruitDelivery}'s own canonical constructor already
 * throws on an invalid quantity, so any {@code FruitDelivery} instance that exists is valid by
 * construction. Only the failure case needs a dedicated type to carry error messages.
 */
public sealed interface ParsedFruitDelivery permits FruitDelivery, ParsedFruitDelivery.Invalid {
	record Invalid(List<String> errors) implements ParsedFruitDelivery {}
}
