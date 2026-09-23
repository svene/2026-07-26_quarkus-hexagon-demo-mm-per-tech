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
	 * For trusted data only - throws {@link IllegalArgumentException} on a violation.
	 * Untrusted input (anything an inbound adapter receives) must go through {@link #parse(String, int)};
	 * enforced by {@code ArchitectureTest}.
	 */
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
