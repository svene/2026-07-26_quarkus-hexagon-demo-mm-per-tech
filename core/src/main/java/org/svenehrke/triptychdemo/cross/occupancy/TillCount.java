package org.svenehrke.triptychdemo.cross.occupancy;

import org.svenehrke.triptychdemo.cross.location.Store;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.lang.reflect.Constructor;
import java.util.Set;

import static org.svenehrke.triptychdemo.cross.validation.ConstructorValidation.declaredConstructor;
import static org.svenehrke.triptychdemo.cross.validation.ConstructorValidation.validateParameters;

/** How many tills a store should have open (head office, demo): at least one, at most {@value #MAX_TILLS}. */
public record TillCount(@NotNull Store store, @Min(1) @Max(MAX_TILLS) int tills) implements ParsedTillCount {

	public static final int MAX_TILLS = 8;

	private static final Constructor<TillCount> CANONICAL_CONSTRUCTOR =
		declaredConstructor(TillCount.class, Store.class, int.class);

	/** For trusted data only - throws {@link IllegalArgumentException} on a violation; untrusted input uses parse(). */
	public TillCount {
		Set<ConstraintViolation<TillCount>> violations = validateParameters(CANONICAL_CONSTRUCTOR, store, tills);
		if (!violations.isEmpty()) {
			throw new IllegalArgumentException(violations.iterator().next().getMessage());
		}
	}

	public static ParsedTillCount parse(Store store, int tills) {
		Set<ConstraintViolation<TillCount>> violations = validateParameters(CANONICAL_CONSTRUCTOR, store, tills);
		return violations.isEmpty() ? new TillCount(store, tills) : new ParsedTillCount.Invalid(violations);
	}
}
