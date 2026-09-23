package org.svenehrke.triptychdemo.feature.vegetable;

import jakarta.validation.ConstraintViolation;

import java.util.Set;

/**
 * No separate "Valid" wrapper is needed: {@link VegetableDelivery}'s own canonical constructor already
 * throws on an invalid quantity, so any {@code VegetableDelivery} instance that exists is valid by
 * construction. Only the failure case needs a dedicated type to carry error messages.
 */
public sealed interface ParsedVegetableDelivery permits VegetableDelivery, ParsedVegetableDelivery.Invalid {
	record Invalid(Set<ConstraintViolation<VegetableDelivery>> violations) implements ParsedVegetableDelivery {}
}
