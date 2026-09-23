package org.svenehrke.triptychdemo.feature.vegetable;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.lang.reflect.Constructor;
import java.util.Set;

public record VegetableOrder(@NotBlank String productName, @Min(1) int quantity) implements ParsedVegetableOrder {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	@SuppressWarnings("unchecked")
	private static final Constructor<VegetableOrder> CANONICAL_CONSTRUCTOR =
		(Constructor<VegetableOrder>) VegetableOrder.class.getDeclaredConstructors()[0];

	/**
	 * For trusted data only - throws {@link IllegalArgumentException} on a violation.
	 * Untrusted input (anything an inbound adapter receives) must go through {@link #parse(String, int)};
	 * enforced by {@code ArchitectureTest}.
	 */
	public VegetableOrder {
		Set<ConstraintViolation<VegetableOrder>> violations = validate(productName, quantity);
		if (!violations.isEmpty()) {
			throw new IllegalArgumentException(violations.iterator().next().getMessage());
		}
	}

	public static ParsedVegetableOrder parse(String productName, int quantity) {
		Set<ConstraintViolation<VegetableOrder>> violations = validate(productName, quantity);
		return violations.isEmpty()
			? new VegetableOrder(productName, quantity)
			: new ParsedVegetableOrder.Invalid(violations);
	}

	private static Set<ConstraintViolation<VegetableOrder>> validate(String productName, int quantity) {
		return VALIDATOR.forExecutables()
			.validateConstructorParameters(CANONICAL_CONSTRUCTOR, new Object[]{productName, quantity});
	}

}
