package org.svenehrke.triptychdemo.feature.vegetable;

import jakarta.validation.ConstraintViolation;

import java.util.Set;

/**
 * No separate "Valid" wrapper is needed: {@link VegetableOrder}'s own canonical constructor already
 * throws on invalid input, so any {@code VegetableOrder} instance that exists is valid by construction.
 * Only the failure case needs a dedicated type to carry error messages.
 */
public sealed interface ParsedVegetableOrder permits VegetableOrder, ParsedVegetableOrder.Invalid {
	record Invalid(Set<ConstraintViolation<VegetableOrder>> violations) implements ParsedVegetableOrder {}
}
