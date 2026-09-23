package org.svenehrke.triptychdemo.feature.meat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.lang.reflect.Constructor;
import java.util.Set;

public record MeatOrder(@NotBlank String productName, @Min(1) int quantity) implements ParsedMeatOrder {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	@SuppressWarnings("unchecked")
	private static final Constructor<MeatOrder> CANONICAL_CONSTRUCTOR =
		(Constructor<MeatOrder>) MeatOrder.class.getDeclaredConstructors()[0];

	/**
	 * @deprecated Use {@link #parse(String, int)} instead, which returns a
	 * {@link ParsedMeatOrder} rather than throwing on invalid input.
	 * Only intended to be used by trusted callers that have no graceful way to react to a violation
	 * (e.g. the HTML admin form, which has no upstream validation step of its own yet).
	 */
	@Deprecated
	public MeatOrder {
		Set<ConstraintViolation<MeatOrder>> violations = validate(productName, quantity);
		if (!violations.isEmpty()) {
			throw new IllegalArgumentException(violations.iterator().next().getMessage());
		}
	}

	public static ParsedMeatOrder parse(String productName, int quantity) {
		Set<ConstraintViolation<MeatOrder>> violations = validate(productName, quantity);
		return violations.isEmpty()
			? new MeatOrder(productName, quantity)
			: new ParsedMeatOrder.Invalid(violations);
	}

	private static Set<ConstraintViolation<MeatOrder>> validate(String productName, int quantity) {
		return VALIDATOR.forExecutables()
			.validateConstructorParameters(CANONICAL_CONSTRUCTOR, new Object[]{productName, quantity});
	}

}
