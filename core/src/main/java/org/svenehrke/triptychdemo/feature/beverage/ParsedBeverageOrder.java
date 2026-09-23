package org.svenehrke.triptychdemo.feature.beverage;

import jakarta.validation.ConstraintViolation;

import java.util.Set;

/**
 * No separate "Valid" wrapper is needed: {@link BeverageOrder}'s own canonical constructor already
 * throws on invalid input, so any {@code BeverageOrder} instance that exists is valid by construction.
 * Only the failure case needs a dedicated type to carry error messages.
 */
public sealed interface ParsedBeverageOrder permits BeverageOrder, ParsedBeverageOrder.Invalid {
	record Invalid(Set<ConstraintViolation<BeverageOrder>> violations) implements ParsedBeverageOrder {}
}
