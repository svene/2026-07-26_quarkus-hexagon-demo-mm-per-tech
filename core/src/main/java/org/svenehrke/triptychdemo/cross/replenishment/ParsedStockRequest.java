package org.svenehrke.triptychdemo.cross.replenishment;

import jakarta.validation.ConstraintViolation;

import java.util.Set;

/** See {@code ParsedFruitOrder}: a {@link StockRequest} is valid by construction, only failure needs a type. */
public sealed interface ParsedStockRequest permits StockRequest, ParsedStockRequest.Invalid {
	record Invalid(Set<ConstraintViolation<StockRequest>> violations) implements ParsedStockRequest {}
}
