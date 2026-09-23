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
	 * @deprecated Use {@link #parse(String, int)} instead, which returns a
	 * {@link ParsedNonFoodOrder} rather than throwing on invalid input.
	 * Only intended to be used by trusted callers that have no graceful way to react to a violation
	 * (e.g. the HTML admin form, which has no upstream validation step of its own yet).
	 */
	@Deprecated
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
