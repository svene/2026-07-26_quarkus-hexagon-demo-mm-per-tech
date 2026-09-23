package org.svenehrke.triptychdemo.feature.beverage;

import jakarta.validation.ConstraintViolation;

import java.util.Set;

/**
 * No separate "Valid" wrapper is needed: {@link BeverageDelivery}'s own canonical constructor already
 * throws on an invalid quantity, so any {@code BeverageDelivery} instance that exists is valid by
 * construction. Only the failure case needs a dedicated type to carry error messages.
 */
public sealed interface ParsedBeverageDelivery permits BeverageDelivery, ParsedBeverageDelivery.Invalid {
	record Invalid(Set<ConstraintViolation<BeverageDelivery>> violations) implements ParsedBeverageDelivery {}
}
