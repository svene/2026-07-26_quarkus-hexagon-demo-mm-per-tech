package org.svenehrke.triptychdemo.cross.replenishment;

import org.svenehrke.triptychdemo.cross.location.Locations;
import jakarta.validation.ConstraintViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockRequestTest {

	@ParameterizedTest
	@ValueSource(strings = {"1", " 42 ", "+7", "2000"})
	void parseText_returnsValidStockRequest(String quantity) {
		ParsedStockRequest result = StockRequest.parse(Locations.BERN, "Apple", quantity);

		assertThat(result).isEqualTo(new StockRequest(Locations.BERN, "Apple", Integer.parseInt(quantity.trim())));
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "1.5", "1234567890"})
	void parseText_returnsInvalid_forNonNumericQuantities(String quantity) {
		assertThat(messages(StockRequest.parse(Locations.BERN, "Apple", quantity))).containsExactly("must be a number");
	}

	@Test
	void parseText_returnsInvalid_forBlankQuantity() {
		assertThat(messages(StockRequest.parse(Locations.BERN, "Apple", " "))).containsExactly("must not be blank");
	}

	@Test
	void parse_appliesTheSupplierOrderLimits() {
		assertThat(messages(StockRequest.parse(Locations.BERN, "Apple", 0))).containsExactly("must be greater than or equal to 1");
		assertThat(messages(StockRequest.parse(Locations.BERN, "Apple", 2001))).containsExactly("must be less than or equal to 2000");
	}

	@Test
	void parse_returnsInvalid_forBlankProductName() {
		assertThat(messages(StockRequest.parse(Locations.BERN, " ", "5"))).containsExactly("must not be blank");
	}

	@Test
	void constructor_throws_forInvalidValues() {
		assertThatThrownBy(() -> new StockRequest(Locations.BERN, "Apple", 0)).isInstanceOf(IllegalArgumentException.class);
	}

	private static java.util.List<String> messages(ParsedStockRequest result) {
		assertThat(result).isInstanceOf(ParsedStockRequest.Invalid.class);
		return ((ParsedStockRequest.Invalid) result).violations().stream().map(ConstraintViolation::getMessage).toList();
	}
}
