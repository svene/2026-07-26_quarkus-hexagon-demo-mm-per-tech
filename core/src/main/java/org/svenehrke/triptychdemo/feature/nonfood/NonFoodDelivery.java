package org.svenehrke.triptychdemo.feature.nonfood;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.lang.reflect.Constructor;
import java.util.Set;

import static org.svenehrke.triptychdemo.cross.validation.ConstructorValidation.declaredConstructor;
import static org.svenehrke.triptychdemo.cross.validation.ConstructorValidation.validateParameters;

public record NonFoodDelivery(@NotBlank String productName, @Min(1) @Max(MAX_QUANTITY) int quantity) implements ParsedNonFoodDelivery {

	static final int MAX_QUANTITY = 10_000;

	private static final Constructor<NonFoodDelivery> CANONICAL_CONSTRUCTOR = declaredConstructor(NonFoodDelivery.class, String.class, int.class);

	/**
	 * For trusted data only - throws {@link IllegalArgumentException} on a violation.
	 * Untrusted input (anything an inbound adapter receives) must go through {@link #parse(String, int)};
	 * enforced by {@code ArchitectureTest}.
	 */
	public NonFoodDelivery {
		Set<ConstraintViolation<NonFoodDelivery>> violations = validate(productName, quantity);
		if (!violations.isEmpty()) {
			throw new IllegalArgumentException(violations.iterator().next().getMessage());
		}
	}

	public static ParsedNonFoodDelivery parse(String productName, int quantity) {
		Set<ConstraintViolation<NonFoodDelivery>> violations = validate(productName, quantity);
		return violations.isEmpty()
			? new NonFoodDelivery(productName, quantity)
			: new ParsedNonFoodDelivery.Invalid(violations);
	}

	private static Set<ConstraintViolation<NonFoodDelivery>> validate(String productName, int quantity) {
		return validateParameters(CANONICAL_CONSTRUCTOR, productName, quantity);
	}

}
