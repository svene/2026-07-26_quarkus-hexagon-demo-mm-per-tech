package org.svenehrke.triptychdemo.cross.purchase;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.lang.reflect.Constructor;
import java.util.Set;

public record PurchaseItem(@NotBlank String productName, @Min(1) int quantity) implements ParsedPurchaseItem {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	@SuppressWarnings("unchecked")
	private static final Constructor<PurchaseItem> CANONICAL_CONSTRUCTOR =
		(Constructor<PurchaseItem>) PurchaseItem.class.getDeclaredConstructors()[0];

	/**
	 * For trusted data only - throws {@link IllegalArgumentException} on a violation.
	 * Untrusted input (anything an inbound adapter receives) must go through {@link #parse(String, int)};
	 * enforced by {@code ArchitectureTest}.
	 */
	public PurchaseItem {
		Set<ConstraintViolation<PurchaseItem>> violations = validate(productName, quantity);
		if (!violations.isEmpty()) {
			throw new IllegalArgumentException(violations.iterator().next().getMessage());
		}
	}

	public static ParsedPurchaseItem parse(String productName, int quantity) {
		Set<ConstraintViolation<PurchaseItem>> violations = validate(productName, quantity);
		return violations.isEmpty()
			? new PurchaseItem(productName, quantity)
			: new ParsedPurchaseItem.Invalid(violations);
	}

	private static Set<ConstraintViolation<PurchaseItem>> validate(String productName, int quantity) {
		return VALIDATOR.forExecutables()
			.validateConstructorParameters(CANONICAL_CONSTRUCTOR, new Object[]{productName, quantity});
	}

}
