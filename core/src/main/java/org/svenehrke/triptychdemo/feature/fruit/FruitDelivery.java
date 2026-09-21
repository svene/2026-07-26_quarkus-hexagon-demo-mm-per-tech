package org.svenehrke.triptychdemo.feature.fruit;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record FruitDelivery(@NotBlank String productName, @Min(1) @Max(MAX_QUANTITY) int quantity) implements ParsedFruitDelivery {

	static final int MAX_QUANTITY = 10_000;

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	/**
	 * @deprecated Use {@link #parse(int)} instead, which returns an
	 * {@link Optional} rather than throwing on invalid input.
	 * Only intended to be used by deserialization tools like Jackson
	 */
	@Deprecated
	public FruitDelivery {
		Set<ConstraintViolation<FruitDelivery>> violations = validate(productName, quantity);
		if (!violations.isEmpty()) {
			throw new IllegalArgumentException(violations.iterator().next().getMessage());
		}
	}

	public static ParsedFruitDelivery parse(String productName, int quantity) {
		Set<ConstraintViolation<FruitDelivery>> violations = validate(productName, quantity);
		return violations.isEmpty()
			? new FruitDelivery(productName, quantity)
			: new ParsedFruitDelivery.Invalid(violations);
	}

	private static Set<ConstraintViolation<FruitDelivery>> validate(String productName, int quantity) {
		return Stream.concat(
				VALIDATOR.validateValue(FruitDelivery.class, "productName", productName).stream(),
				VALIDATOR.validateValue(FruitDelivery.class, "quantity", quantity).stream())
			.collect(Collectors.toSet());
	}

}
