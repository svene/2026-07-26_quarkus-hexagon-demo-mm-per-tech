package org.svenehrke.triptychdemo.cross.replenishment;

import org.svenehrke.triptychdemo.cross.location.Replenished;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.lang.reflect.Constructor;
import java.util.Set;

import static org.svenehrke.triptychdemo.cross.validation.ConstructorValidation.declaredConstructor;
import static org.svenehrke.triptychdemo.cross.validation.ConstructorValidation.validateParameters;

/**
 * What a store or the online FC asks the DC for - the DC itself cannot, it is no {@link Replenished}. Once stored
 * it is a {@link ReplenishmentRequest}. Same parse() mechanism and limits as the supplier orders (see
 * {@code FruitOrder}, validation.md).
 */
public record StockRequest(@NotNull Replenished location, @NotBlank String productName, @Min(1) @Max(2000) int quantity)
	implements ParsedStockRequest {

	private static final Constructor<StockRequest> CANONICAL_CONSTRUCTOR =
		declaredConstructor(StockRequest.class, Replenished.class, String.class, int.class);
	private static final Constructor<StockRequest> TEXT_CONSTRUCTOR =
		declaredConstructor(StockRequest.class, Replenished.class, String.class, String.class);

	/** For trusted data only - throws {@link IllegalArgumentException} on a violation; untrusted input uses parse(). */
	public StockRequest {
		Set<ConstraintViolation<StockRequest>> violations = validate(location, productName, quantity);
		if (!violations.isEmpty()) {
			throw new IllegalArgumentException(violations.iterator().next().getMessage());
		}
	}

	/** Never invoked: only carries the text constraints for {@link #parse(Replenished, String, String)}. */
	private StockRequest(@NotNull Replenished location, @NotBlank String productName,
	                     @NotBlank @Pattern(regexp = "\\s*([+-]?\\d{1,9}\\s*)?", message = "must be a number") String quantity) {
		this(location, productName, Integer.parseInt(quantity.trim()));
	}

	public static ParsedStockRequest parse(Replenished location, String productName, String quantity) {
		Set<ConstraintViolation<StockRequest>> violations = validateParameters(TEXT_CONSTRUCTOR, location, productName, quantity);
		return violations.isEmpty()
			? parse(location, productName, Integer.parseInt(quantity.trim()))
			: new ParsedStockRequest.Invalid(violations);
	}

	public static ParsedStockRequest parse(Replenished location, String productName, int quantity) {
		Set<ConstraintViolation<StockRequest>> violations = validate(location, productName, quantity);
		return violations.isEmpty()
			? new StockRequest(location, productName, quantity)
			: new ParsedStockRequest.Invalid(violations);
	}

	private static Set<ConstraintViolation<StockRequest>> validate(Replenished location, String productName, int quantity) {
		return validateParameters(CANONICAL_CONSTRUCTOR, location, productName, quantity);
	}
}
