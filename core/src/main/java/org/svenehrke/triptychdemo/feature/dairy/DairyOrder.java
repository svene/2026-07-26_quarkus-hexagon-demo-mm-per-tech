package org.svenehrke.triptychdemo.feature.dairy;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.lang.reflect.Constructor;
import java.util.Set;

public record DairyOrder(@NotBlank String productName, @Min(1) int quantity) implements ParsedDairyOrder {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	@SuppressWarnings("unchecked")
	private static final Constructor<DairyOrder> CANONICAL_CONSTRUCTOR =
		(Constructor<DairyOrder>) DairyOrder.class.getDeclaredConstructors()[0];

	/**
	 * @deprecated Use {@link #parse(String, int)} instead, which returns a
	 * {@link ParsedDairyOrder} rather than throwing on invalid input.
	 * Only intended to be used by trusted callers that have no graceful way to react to a violation
	 * (e.g. the HTML admin form, which has no upstream validation step of its own yet).
	 */
	@Deprecated
	public DairyOrder {
		Set<ConstraintViolation<DairyOrder>> violations = validate(productName, quantity);
		if (!violations.isEmpty()) {
			throw new IllegalArgumentException(violations.iterator().next().getMessage());
		}
	}

	public static ParsedDairyOrder parse(String productName, int quantity) {
		Set<ConstraintViolation<DairyOrder>> violations = validate(productName, quantity);
		return violations.isEmpty()
			? new DairyOrder(productName, quantity)
			: new ParsedDairyOrder.Invalid(violations);
	}

	private static Set<ConstraintViolation<DairyOrder>> validate(String productName, int quantity) {
		return VALIDATOR.forExecutables()
			.validateConstructorParameters(CANONICAL_CONSTRUCTOR, new Object[]{productName, quantity});
	}

}
