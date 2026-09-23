package org.svenehrke.triptychdemo.feature.dairy;

import jakarta.validation.ConstraintViolation;

import java.util.Set;

/**
 * No separate "Valid" wrapper is needed: {@link DairyOrder}'s own canonical constructor already
 * throws on invalid input, so any {@code DairyOrder} instance that exists is valid by construction.
 * Only the failure case needs a dedicated type to carry error messages.
 */
public sealed interface ParsedDairyOrder permits DairyOrder, ParsedDairyOrder.Invalid {
	record Invalid(Set<ConstraintViolation<DairyOrder>> violations) implements ParsedDairyOrder {}
}
