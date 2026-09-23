package org.svenehrke.triptychdemo.feature.nonfood;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.lang.reflect.Constructor;
import java.util.Set;

public record NonFoodDelivery(@NotBlank String productName, @Min(1) @Max(MAX_QUANTITY) int quantity) implements ParsedNonFoodDelivery {

	static final int MAX_QUANTITY = 10_000;

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	@SuppressWarnings("unchecked")
	private static final Constructor<NonFoodDelivery> CANONICAL_CONSTRUCTOR =
		(Constructor<NonFoodDelivery>) NonFoodDelivery.class.getDeclaredConstructors()[0];

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
		return VALIDATOR.forExecutables()
			.validateConstructorParameters(CANONICAL_CONSTRUCTOR, new Object[]{productName, quantity});
	}

}
