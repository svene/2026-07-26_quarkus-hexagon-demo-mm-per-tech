package org.svenehrke.triptychdemo.feature.nonfood;

import jakarta.validation.ConstraintViolation;

import java.util.Set;

/**
 * No separate "Valid" wrapper is needed: {@link NonFoodDelivery}'s own canonical constructor already
 * throws on an invalid quantity, so any {@code NonFoodDelivery} instance that exists is valid by
 * construction. Only the failure case needs a dedicated type to carry error messages.
 */
public sealed interface ParsedNonFoodDelivery permits NonFoodDelivery, ParsedNonFoodDelivery.Invalid {
	record Invalid(Set<ConstraintViolation<NonFoodDelivery>> violations) implements ParsedNonFoodDelivery {}
}
