package org.svenehrke.triptychdemo.feature.dairy;

import jakarta.validation.ConstraintViolation;

import java.util.Set;

/**
 * No separate "Valid" wrapper is needed: {@link DairyDelivery}'s own canonical constructor already
 * throws on an invalid quantity, so any {@code DairyDelivery} instance that exists is valid by
 * construction. Only the failure case needs a dedicated type to carry error messages.
 */
public sealed interface ParsedDairyDelivery permits DairyDelivery, ParsedDairyDelivery.Invalid {
	record Invalid(Set<ConstraintViolation<DairyDelivery>> violations) implements ParsedDairyDelivery {}
}
