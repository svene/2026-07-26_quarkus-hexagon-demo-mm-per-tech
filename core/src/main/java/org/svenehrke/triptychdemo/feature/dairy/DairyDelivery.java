package org.svenehrke.triptychdemo.feature.dairy;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.lang.reflect.Constructor;
import java.util.Set;

public record DairyDelivery(@NotBlank String productName, @Min(1) @Max(MAX_QUANTITY) int quantity) implements ParsedDairyDelivery {

	static final int MAX_QUANTITY = 10_000;

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	@SuppressWarnings("unchecked")
	private static final Constructor<DairyDelivery> CANONICAL_CONSTRUCTOR =
		(Constructor<DairyDelivery>) DairyDelivery.class.getDeclaredConstructors()[0];

	/**
	 * For trusted data only - throws {@link IllegalArgumentException} on a violation.
	 * Untrusted input (anything an inbound adapter receives) must go through {@link #parse(String, int)};
	 * enforced by {@code ArchitectureTest}.
	 */
	public DairyDelivery {
		Set<ConstraintViolation<DairyDelivery>> violations = validate(productName, quantity);
		if (!violations.isEmpty()) {
			throw new IllegalArgumentException(violations.iterator().next().getMessage());
		}
	}

	public static ParsedDairyDelivery parse(String productName, int quantity) {
		Set<ConstraintViolation<DairyDelivery>> violations = validate(productName, quantity);
		return violations.isEmpty()
			? new DairyDelivery(productName, quantity)
			: new ParsedDairyDelivery.Invalid(violations);
	}

	private static Set<ConstraintViolation<DairyDelivery>> validate(String productName, int quantity) {
		return VALIDATOR.forExecutables()
			.validateConstructorParameters(CANONICAL_CONSTRUCTOR, new Object[]{productName, quantity});
	}

}
