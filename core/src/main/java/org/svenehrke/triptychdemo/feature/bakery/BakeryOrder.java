package org.svenehrke.triptychdemo.feature.bakery;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.lang.reflect.Constructor;
import java.util.Set;

public record BakeryOrder(@NotBlank String productName, @Min(1) int quantity) implements ParsedBakeryOrder {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	@SuppressWarnings("unchecked")
	private static final Constructor<BakeryOrder> CANONICAL_CONSTRUCTOR =
		(Constructor<BakeryOrder>) BakeryOrder.class.getDeclaredConstructors()[0];

	/**
	 * @deprecated Use {@link #parse(String, int)} instead, which returns a
	 * {@link ParsedBakeryOrder} rather than throwing on invalid input.
	 * Only intended to be used by trusted callers that have no graceful way to react to a violation
	 * (e.g. the HTML admin form, which has no upstream validation step of its own yet).
	 */
	@Deprecated
	public BakeryOrder {
		Set<ConstraintViolation<BakeryOrder>> violations = validate(productName, quantity);
		if (!violations.isEmpty()) {
			throw new IllegalArgumentException(violations.iterator().next().getMessage());
		}
	}

	public static ParsedBakeryOrder parse(String productName, int quantity) {
		Set<ConstraintViolation<BakeryOrder>> violations = validate(productName, quantity);
		return violations.isEmpty()
			? new BakeryOrder(productName, quantity)
			: new ParsedBakeryOrder.Invalid(violations);
	}

	private static Set<ConstraintViolation<BakeryOrder>> validate(String productName, int quantity) {
		return VALIDATOR.forExecutables()
			.validateConstructorParameters(CANONICAL_CONSTRUCTOR, new Object[]{productName, quantity});
	}

}
