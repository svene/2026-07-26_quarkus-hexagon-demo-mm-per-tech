package org.svenehrke.triptychdemo.feature.beverage;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.lang.reflect.Constructor;
import java.util.Set;

public record BeverageOrder(@NotBlank String productName, @Min(1) int quantity) implements ParsedBeverageOrder {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	@SuppressWarnings("unchecked")
	private static final Constructor<BeverageOrder> CANONICAL_CONSTRUCTOR =
		(Constructor<BeverageOrder>) BeverageOrder.class.getDeclaredConstructors()[0];

	/**
	 * For trusted data only - throws {@link IllegalArgumentException} on a violation.
	 * Untrusted input (anything an inbound adapter receives) must go through {@link #parse(String, int)};
	 * enforced by {@code ArchitectureTest}.
	 */
	public BeverageOrder {
		Set<ConstraintViolation<BeverageOrder>> violations = validate(productName, quantity);
		if (!violations.isEmpty()) {
			throw new IllegalArgumentException(violations.iterator().next().getMessage());
		}
	}

	public static ParsedBeverageOrder parse(String productName, int quantity) {
		Set<ConstraintViolation<BeverageOrder>> violations = validate(productName, quantity);
		return violations.isEmpty()
			? new BeverageOrder(productName, quantity)
			: new ParsedBeverageOrder.Invalid(violations);
	}

	private static Set<ConstraintViolation<BeverageOrder>> validate(String productName, int quantity) {
		return VALIDATOR.forExecutables()
			.validateConstructorParameters(CANONICAL_CONSTRUCTOR, new Object[]{productName, quantity});
	}

}
