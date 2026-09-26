package org.svenehrke.triptychdemo.feature.fruit;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.lang.reflect.Constructor;
import java.util.Set;

import static org.svenehrke.triptychdemo.cross.validation.ConstructorValidation.declaredConstructor;
import static org.svenehrke.triptychdemo.cross.validation.ConstructorValidation.validateParameters;

public record FruitDelivery(@NotBlank String productName, @Min(1) @Max(MAX_QUANTITY) int quantity) implements ParsedFruitDelivery {

	static final int MAX_QUANTITY = 10_000;

	private static final Constructor<FruitDelivery> CANONICAL_CONSTRUCTOR = declaredConstructor(FruitDelivery.class, String.class, int.class);

	/**
	 * For trusted data only - throws {@link IllegalArgumentException} on a violation.
	 * Untrusted input (anything an inbound adapter receives) must go through {@link #parse(String, int)};
	 * enforced by {@code ArchitectureTest}.
	 */
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
		return validateParameters(CANONICAL_CONSTRUCTOR, productName, quantity);
	}

}
