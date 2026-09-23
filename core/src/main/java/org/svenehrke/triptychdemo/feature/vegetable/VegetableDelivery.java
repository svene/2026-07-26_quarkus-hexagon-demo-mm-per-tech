package org.svenehrke.triptychdemo.feature.vegetable;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.lang.reflect.Constructor;
import java.util.Set;

public record VegetableDelivery(@NotBlank String productName, @Min(1) @Max(MAX_QUANTITY) int quantity) implements ParsedVegetableDelivery {

	static final int MAX_QUANTITY = 10_000;

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	@SuppressWarnings("unchecked")
	private static final Constructor<VegetableDelivery> CANONICAL_CONSTRUCTOR =
		(Constructor<VegetableDelivery>) VegetableDelivery.class.getDeclaredConstructors()[0];

	/**
	 * For trusted data only - throws {@link IllegalArgumentException} on a violation.
	 * Untrusted input (anything an inbound adapter receives) must go through {@link #parse(String, int)};
	 * enforced by {@code ArchitectureTest}.
	 */
	public VegetableDelivery {
		Set<ConstraintViolation<VegetableDelivery>> violations = validate(productName, quantity);
		if (!violations.isEmpty()) {
			throw new IllegalArgumentException(violations.iterator().next().getMessage());
		}
	}

	public static ParsedVegetableDelivery parse(String productName, int quantity) {
		Set<ConstraintViolation<VegetableDelivery>> violations = validate(productName, quantity);
		return violations.isEmpty()
			? new VegetableDelivery(productName, quantity)
			: new ParsedVegetableDelivery.Invalid(violations);
	}

	private static Set<ConstraintViolation<VegetableDelivery>> validate(String productName, int quantity) {
		return VALIDATOR.forExecutables()
			.validateConstructorParameters(CANONICAL_CONSTRUCTOR, new Object[]{productName, quantity});
	}

}
