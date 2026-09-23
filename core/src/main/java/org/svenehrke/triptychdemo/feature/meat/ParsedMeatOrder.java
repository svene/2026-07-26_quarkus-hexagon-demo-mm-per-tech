package org.svenehrke.triptychdemo.feature.meat;

import jakarta.validation.ConstraintViolation;

import java.util.Set;

/**
 * No separate "Valid" wrapper is needed: {@link MeatOrder}'s own canonical constructor already
 * throws on invalid input, so any {@code MeatOrder} instance that exists is valid by construction.
 * Only the failure case needs a dedicated type to carry error messages.
 */
public sealed interface ParsedMeatOrder permits MeatOrder, ParsedMeatOrder.Invalid {
	record Invalid(Set<ConstraintViolation<MeatOrder>> violations) implements ParsedMeatOrder {}
}
