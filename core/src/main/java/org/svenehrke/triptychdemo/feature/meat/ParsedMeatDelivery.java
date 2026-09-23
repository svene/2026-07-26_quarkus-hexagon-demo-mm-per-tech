package org.svenehrke.triptychdemo.feature.meat;

import jakarta.validation.ConstraintViolation;

import java.util.Set;

/**
 * No separate "Valid" wrapper is needed: {@link MeatDelivery}'s own canonical constructor already
 * throws on an invalid quantity, so any {@code MeatDelivery} instance that exists is valid by
 * construction. Only the failure case needs a dedicated type to carry error messages.
 */
public sealed interface ParsedMeatDelivery permits MeatDelivery, ParsedMeatDelivery.Invalid {
	record Invalid(Set<ConstraintViolation<MeatDelivery>> violations) implements ParsedMeatDelivery {}
}
