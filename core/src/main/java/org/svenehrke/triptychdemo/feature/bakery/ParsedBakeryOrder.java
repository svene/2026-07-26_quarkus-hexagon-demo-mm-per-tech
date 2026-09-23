package org.svenehrke.triptychdemo.feature.bakery;

import jakarta.validation.ConstraintViolation;

import java.util.Set;

/**
 * No separate "Valid" wrapper is needed: {@link BakeryOrder}'s own canonical constructor already
 * throws on invalid input, so any {@code BakeryOrder} instance that exists is valid by construction.
 * Only the failure case needs a dedicated type to carry error messages.
 */
public sealed interface ParsedBakeryOrder permits BakeryOrder, ParsedBakeryOrder.Invalid {
	record Invalid(Set<ConstraintViolation<BakeryOrder>> violations) implements ParsedBakeryOrder {}
}
