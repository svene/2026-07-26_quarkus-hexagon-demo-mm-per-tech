package org.svenehrke.triptychdemo.feature.nonfood;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.lang.reflect.Constructor;
import java.util.Set;

public record NonFoodOrder(@NotBlank String productName, @Min(1) int quantity) implements ParsedNonFoodOrder {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	@SuppressWarnings("unchecked")
	private static final Constructor<NonFoodOrder> CANONICAL_CONSTRUCTOR =
		(Constructor<NonFoodOrder>) NonFoodOrder.class.getDeclaredConstructors()[0];

	/**
	 * For trusted data only - throws {@link IllegalArgumentException} on a violation.
	 * Untrusted input (anything an inbound adapter receives) must go through {@link #parse(String, int)};
	 * enforced by {@code ArchitectureTest}.
	 */
	public NonFoodOrder {
		Set<ConstraintViolation<NonFoodOrder>> violations = validate(productName, quantity);
		if (!violations.isEmpty()) {
			throw new IllegalArgumentException(violations.iterator().next().getMessage());
		}
	}

	public static ParsedNonFoodOrder parse(String productName, int quantity) {
		Set<ConstraintViolation<NonFoodOrder>> violations = validate(productName, quantity);
		return violations.isEmpty()
			? new NonFoodOrder(productName, quantity)
			: new ParsedNonFoodOrder.Invalid(violations);
	}

	private static Set<ConstraintViolation<NonFoodOrder>> validate(String productName, int quantity) {
		return VALIDATOR.forExecutables()
			.validateConstructorParameters(CANONICAL_CONSTRUCTOR, new Object[]{productName, quantity});
	}

}
