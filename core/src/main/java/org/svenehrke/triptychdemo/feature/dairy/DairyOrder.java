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
	 * For trusted data only - throws {@link IllegalArgumentException} on a violation.
	 * Untrusted input (anything an inbound adapter receives) must go through {@link #parse(String, int)};
	 * enforced by {@code ArchitectureTest}.
	 */
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
