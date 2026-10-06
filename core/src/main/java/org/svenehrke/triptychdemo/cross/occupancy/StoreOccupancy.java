package org.svenehrke.triptychdemo.cross.occupancy;

import org.svenehrke.triptychdemo.cross.location.Store;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.Set;

import static org.svenehrke.triptychdemo.cross.validation.ConstructorValidation.declaredConstructor;
import static org.svenehrke.triptychdemo.cross.validation.ConstructorValidation.validateParameters;

/**
 * What a store's checkout system reports at {@code measuredAt}: customers inside (at most {@code capacity}), how many
 * of them queue for a till, how many of the open tills are busy, and how many customers were turned away at the full
 * store in the last demo day. A snapshot, not an event: only the latest one per store counts, so storing
 * it is idempotent (see {@link OccupancyHandler#record(StoreOccupancy)}).
 */
public record StoreOccupancy(@NotNull Store store, @NotNull Instant measuredAt, @Min(0) int inside, @Min(1) int capacity,
                             @Min(0) int queuing, @Min(1) @Max(TillCount.MAX_TILLS) int tills, @Min(0) int tillsBusy,
                             @Min(0) int turnedAway)
	implements ParsedStoreOccupancy {

	private static final Constructor<StoreOccupancy> CANONICAL_CONSTRUCTOR = declaredConstructor(StoreOccupancy.class,
		Store.class, Instant.class, int.class, int.class, int.class, int.class, int.class, int.class);

	/** For trusted data only - throws {@link IllegalArgumentException} on a violation; untrusted input uses parse(). */
	public StoreOccupancy {
		Set<ConstraintViolation<StoreOccupancy>> violations = validate(store, measuredAt, inside, capacity, queuing, tills, tillsBusy, turnedAway);
		if (!violations.isEmpty()) {
			throw new IllegalArgumentException(violations.iterator().next().getMessage());
		}
	}

	public static ParsedStoreOccupancy parse(Store store, Instant measuredAt, int inside, int capacity, int queuing,
	                                         int tills, int tillsBusy, int turnedAway) {
		Set<ConstraintViolation<StoreOccupancy>> violations = validate(store, measuredAt, inside, capacity, queuing, tills, tillsBusy, turnedAway);
		return violations.isEmpty()
			? new StoreOccupancy(store, measuredAt, inside, capacity, queuing, tills, tillsBusy, turnedAway)
			: new ParsedStoreOccupancy.Invalid(violations);
	}

	private static Set<ConstraintViolation<StoreOccupancy>> validate(Store store, Instant measuredAt, int inside,
	                                                                 int capacity, int queuing, int tills,
	                                                                 int tillsBusy, int turnedAway) {
		return validateParameters(CANONICAL_CONSTRUCTOR, store, measuredAt, inside, capacity, queuing, tills, tillsBusy,
			turnedAway);
	}
}
