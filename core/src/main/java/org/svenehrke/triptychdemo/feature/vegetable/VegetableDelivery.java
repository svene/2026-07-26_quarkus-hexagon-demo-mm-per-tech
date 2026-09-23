package org.svenehrke.triptychdemo.feature.vegetable;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.lang.reflect.Constructor;
import java.util.Optional;
import java.util.Set;

public record VegetableDelivery(@NotBlank String productName, @Min(1) @Max(MAX_QUANTITY) int quantity) implements ParsedVegetableDelivery {

	static final int MAX_QUANTITY = 10_000;

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	@SuppressWarnings("unchecked")
	private static final Constructor<VegetableDelivery> CANONICAL_CONSTRUCTOR =
		(Constructor<VegetableDelivery>) VegetableDelivery.class.getDeclaredConstructors()[0];

	/**
	 * @deprecated Use {@link #parse(String, int)} instead, which returns an
	 * {@link Optional} rather than throwing on invalid input.
	 * Only intended to be used by deserialization tools like Jackson
	 */
	@Deprecated
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
