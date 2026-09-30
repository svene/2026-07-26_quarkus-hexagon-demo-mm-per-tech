package org.svenehrke.triptychdemo.cross.purchase;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.lang.reflect.Constructor;
import java.util.Set;

import static org.svenehrke.triptychdemo.cross.validation.ConstructorValidation.declaredConstructor;
import static org.svenehrke.triptychdemo.cross.validation.ConstructorValidation.validateParameters;

public record PurchaseItem(@NotBlank String productName, @Min(1) @Max(50) int quantity) implements ParsedPurchaseItem {

	private static final Constructor<PurchaseItem> CANONICAL_CONSTRUCTOR = declaredConstructor(PurchaseItem.class, String.class, int.class);
	private static final Constructor<PurchaseItem> TEXT_CONSTRUCTOR = declaredConstructor(PurchaseItem.class, String.class, String.class);

	/**
	 * For trusted data only - throws {@link IllegalArgumentException} on a violation.
	 * Untrusted input (anything an inbound adapter receives) must go through {@link #parse(String, int)}
	 * or {@link #parse(String, String)};
	 * enforced by {@code ArchitectureTest}.
	 */
	public PurchaseItem {
		Set<ConstraintViolation<PurchaseItem>> violations = validate(productName, quantity);
		if (!violations.isEmpty()) {
			throw new IllegalArgumentException(violations.iterator().next().getMessage());
		}
	}

	/**
	 * Never invoked: it only exists so Bean Validation can check text input against its parameter
	 * constraints (see {@link #parse(String, String)}). Records require it to delegate to the canonical one.
	 * A blank quantity passes {@code @Pattern} on purpose, so it's reported only as "must not be blank".
	 */
	private PurchaseItem(@NotBlank String productName,
	                     @NotBlank @Pattern(regexp = "\\s*([+-]?\\d{1,9}\\s*)?", message = "must be a number") String quantity) {
		this(productName, Integer.parseInt(quantity.trim()));
	}

	/**
	 * For text input, e.g. an HTML form field: checks that {@code quantity} is a number (at most 9
	 * digits, so it always fits an {@code int}), then applies the same constraints as
	 * {@link #parse(String, int)}.
	 */
	public static ParsedPurchaseItem parse(String productName, String quantity) {
		Set<ConstraintViolation<PurchaseItem>> violations = validateParameters(TEXT_CONSTRUCTOR, productName, quantity);
		return violations.isEmpty()
			? parse(productName, Integer.parseInt(quantity.trim()))
			: new ParsedPurchaseItem.Invalid(violations);
	}

	public static ParsedPurchaseItem parse(String productName, int quantity) {
		Set<ConstraintViolation<PurchaseItem>> violations = validate(productName, quantity);
		return violations.isEmpty()
			? new PurchaseItem(productName, quantity)
			: new ParsedPurchaseItem.Invalid(violations);
	}

	private static Set<ConstraintViolation<PurchaseItem>> validate(String productName, int quantity) {
		return validateParameters(CANONICAL_CONSTRUCTOR, productName, quantity);
	}

}
