package org.svenehrke.triptychdemo.feature.nonfood;

import jakarta.validation.ConstraintViolation;

import java.util.Set;

/**
 * No separate "Valid" wrapper is needed: {@link NonFoodOrder}'s own canonical constructor already
 * throws on invalid input, so any {@code NonFoodOrder} instance that exists is valid by construction.
 * Only the failure case needs a dedicated type to carry error messages.
 */
public sealed interface ParsedNonFoodOrder permits NonFoodOrder, ParsedNonFoodOrder.Invalid {
	record Invalid(Set<ConstraintViolation<NonFoodOrder>> violations) implements ParsedNonFoodOrder {}
}
