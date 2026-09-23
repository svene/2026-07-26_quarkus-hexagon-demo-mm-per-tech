package org.svenehrke.triptychdemo.cross.purchase;

import jakarta.validation.ConstraintViolation;

import java.util.Set;

/**
 * No separate "Valid" wrapper is needed: {@link PurchaseItem}'s own canonical constructor already
 * throws on invalid input, so any {@code PurchaseItem} instance that exists is valid by construction.
 * Only the failure case needs a dedicated type to carry error messages.
 */
public sealed interface ParsedPurchaseItem permits PurchaseItem, ParsedPurchaseItem.Invalid {
	record Invalid(Set<ConstraintViolation<PurchaseItem>> violations) implements ParsedPurchaseItem {}
}
