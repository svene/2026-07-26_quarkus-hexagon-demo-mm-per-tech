package org.svenehrke.triptychdemo.cross.occupancy;

import jakarta.validation.ConstraintViolation;

import java.util.Set;

/** See {@code ParsedFruitOrder}: a {@link TillCount} is valid by construction, only failure needs a type. */
public sealed interface ParsedTillCount permits TillCount, ParsedTillCount.Invalid {
	record Invalid(Set<ConstraintViolation<TillCount>> violations) implements ParsedTillCount {}
}
