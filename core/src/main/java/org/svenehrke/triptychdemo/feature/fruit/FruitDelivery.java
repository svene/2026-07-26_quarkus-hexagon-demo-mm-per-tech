package org.svenehrke.triptychdemo.feature.fruit;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Optional;
import java.util.Set;

public record FruitDelivery(String productName, @Min(1) @Max(MAX_QUANTITY) int quantity) implements ParsedFruitDelivery {

	static final int MAX_QUANTITY = 10_000;

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	/**
	 * @deprecated Use {@link #parse(int)} instead, which returns an
	 * {@link Optional} rather than throwing on invalid input.
	 * Only intended to be used by deserialization tools like Jackson
	 */
	@Deprecated
	public FruitDelivery {
		Set<ConstraintViolation<FruitDelivery>> violations = validate(quantity);
		if (!violations.isEmpty()) {
			throw new IllegalArgumentException(violations.iterator().next().getMessage());
		}
	}

	public static ParsedFruitDelivery parse(String productName, int quantity) {
		// TODO: validate productName
		Set<ConstraintViolation<FruitDelivery>> violations = validate(quantity);
		return violations.isEmpty()
			? new FruitDelivery(productName, quantity)
			: new ParsedFruitDelivery.Invalid(violations);
	}

	private static Set<ConstraintViolation<FruitDelivery>> validate(int quantity) {
		return VALIDATOR.validateValue(FruitDelivery.class, "quantity", quantity);
	}

}
