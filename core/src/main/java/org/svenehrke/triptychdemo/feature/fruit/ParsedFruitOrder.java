package org.svenehrke.triptychdemo.feature.fruit;

import jakarta.validation.ConstraintViolation;

import java.util.Set;

/**
 * No separate "Valid" wrapper is needed: {@link FruitOrder}'s own canonical constructor already
 * throws on invalid input, so any {@code FruitOrder} instance that exists is valid by construction.
 * Only the failure case needs a dedicated type to carry error messages.
 */
public sealed interface ParsedFruitOrder permits FruitOrder, ParsedFruitOrder.Invalid {
	record Invalid(Set<ConstraintViolation<FruitOrder>> violations) implements ParsedFruitOrder {}
}
