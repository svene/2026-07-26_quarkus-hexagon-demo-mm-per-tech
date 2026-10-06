package org.svenehrke.triptychdemo.cross.occupancy;

import jakarta.validation.ConstraintViolation;

import java.util.Set;

/** See {@code ParsedFruitOrder}: a {@link StoreOccupancy} is valid by construction, only failure needs a type. */
public sealed interface ParsedStoreOccupancy permits StoreOccupancy, ParsedStoreOccupancy.Invalid {
	record Invalid(Set<ConstraintViolation<StoreOccupancy>> violations) implements ParsedStoreOccupancy {}
}
