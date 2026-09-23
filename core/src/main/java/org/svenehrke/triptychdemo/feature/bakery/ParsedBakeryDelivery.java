package org.svenehrke.triptychdemo.feature.bakery;

import jakarta.validation.ConstraintViolation;

import java.util.Set;

/**
 * No separate "Valid" wrapper is needed: {@link BakeryDelivery}'s own canonical constructor already
 * throws on an invalid quantity, so any {@code BakeryDelivery} instance that exists is valid by
 * construction. Only the failure case needs a dedicated type to carry error messages.
 */
public sealed interface ParsedBakeryDelivery permits BakeryDelivery, ParsedBakeryDelivery.Invalid {
	record Invalid(Set<ConstraintViolation<BakeryDelivery>> violations) implements ParsedBakeryDelivery {}
}
