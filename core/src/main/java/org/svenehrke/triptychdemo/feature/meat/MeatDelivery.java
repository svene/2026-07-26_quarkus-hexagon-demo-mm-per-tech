package org.svenehrke.triptychdemo.feature.meat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.lang.reflect.Constructor;
import java.util.Set;

import static org.svenehrke.triptychdemo.cross.validation.ConstructorValidation.declaredConstructor;
import static org.svenehrke.triptychdemo.cross.validation.ConstructorValidation.validateParameters;

public record MeatDelivery(@NotBlank String productName, @Min(1) @Max(MAX_QUANTITY) int quantity) implements ParsedMeatDelivery {

	static final int MAX_QUANTITY = 10_000;

	private static final Constructor<MeatDelivery> CANONICAL_CONSTRUCTOR = declaredConstructor(MeatDelivery.class, String.class, int.class);

	/**
	 * For trusted data only - throws {@link IllegalArgumentException} on a violation.
	 * Untrusted input (anything an inbound adapter receives) must go through {@link #parse(String, int)};
	 * enforced by {@code ArchitectureTest}.
	 */
	public MeatDelivery {
		Set<ConstraintViolation<MeatDelivery>> violations = validate(productName, quantity);
		if (!violations.isEmpty()) {
			throw new IllegalArgumentException(violations.iterator().next().getMessage());
		}
	}

	public static ParsedMeatDelivery parse(String productName, int quantity) {
		Set<ConstraintViolation<MeatDelivery>> violations = validate(productName, quantity);
		return violations.isEmpty()
			? new MeatDelivery(productName, quantity)
			: new ParsedMeatDelivery.Invalid(violations);
	}

	private static Set<ConstraintViolation<MeatDelivery>> validate(String productName, int quantity) {
		return validateParameters(CANONICAL_CONSTRUCTOR, productName, quantity);
	}

}
