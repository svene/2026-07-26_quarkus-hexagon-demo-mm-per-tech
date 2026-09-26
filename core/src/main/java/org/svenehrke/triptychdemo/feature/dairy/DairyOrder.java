package org.svenehrke.triptychdemo.feature.dairy;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.lang.reflect.Constructor;
import java.util.Set;

import static org.svenehrke.triptychdemo.cross.validation.ConstructorValidation.declaredConstructor;
import static org.svenehrke.triptychdemo.cross.validation.ConstructorValidation.validateParameters;

public record DairyOrder(@NotBlank String productName, @Min(1) int quantity) implements ParsedDairyOrder {

	private static final Constructor<DairyOrder> CANONICAL_CONSTRUCTOR = declaredConstructor(DairyOrder.class, String.class, int.class);
	private static final Constructor<DairyOrder> TEXT_CONSTRUCTOR = declaredConstructor(DairyOrder.class, String.class, String.class);

	/**
	 * For trusted data only - throws {@link IllegalArgumentException} on a violation.
	 * Untrusted input (anything an inbound adapter receives) must go through {@link #parse(String, int)}
	 * or {@link #parse(String, String)};
	 * enforced by {@code ArchitectureTest}.
	 */
	public DairyOrder {
		Set<ConstraintViolation<DairyOrder>> violations = validate(productName, quantity);
		if (!violations.isEmpty()) {
			throw new IllegalArgumentException(violations.iterator().next().getMessage());
		}
	}

	/**
	 * Never invoked: it only exists so Bean Validation can check text input against its parameter
	 * constraints (see {@link #parse(String, String)}). Records require it to delegate to the canonical one.
	 * A blank quantity passes {@code @Pattern} on purpose, so it's reported only as "must not be blank".
	 */
	private DairyOrder(@NotBlank String productName,
	                   @NotBlank @Pattern(regexp = "\\s*([+-]?\\d{1,9}\\s*)?", message = "must be a number") String quantity) {
		this(productName, Integer.parseInt(quantity.trim()));
	}

	/**
	 * For text input, e.g. an HTML form field: checks that {@code quantity} is a number (at most 9
	 * digits, so it always fits an {@code int}), then applies the same constraints as
	 * {@link #parse(String, int)}.
	 */
	public static ParsedDairyOrder parse(String productName, String quantity) {
		Set<ConstraintViolation<DairyOrder>> violations = validateParameters(TEXT_CONSTRUCTOR, productName, quantity);
		return violations.isEmpty()
			? parse(productName, Integer.parseInt(quantity.trim()))
			: new ParsedDairyOrder.Invalid(violations);
	}

	public static ParsedDairyOrder parse(String productName, int quantity) {
		Set<ConstraintViolation<DairyOrder>> violations = validate(productName, quantity);
		return violations.isEmpty()
			? new DairyOrder(productName, quantity)
			: new ParsedDairyOrder.Invalid(violations);
	}

	private static Set<ConstraintViolation<DairyOrder>> validate(String productName, int quantity) {
		return validateParameters(CANONICAL_CONSTRUCTOR, productName, quantity);
	}

}
