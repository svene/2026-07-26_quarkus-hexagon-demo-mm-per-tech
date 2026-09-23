package org.svenehrke.triptychdemo.feature.beverage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BeverageDeliveryTest {

	// --- Construction: valid cases ---

	@ParameterizedTest
	@ValueSource(ints = {1, 500, 10_000})
	void parse_returnsValidBeverageDelivery_forValidQuantities(int quantity) {
		ParsedBeverageDelivery result = BeverageDelivery.parse("productName", quantity);

		assertThat(result).isInstanceOf(BeverageDelivery.class);
		assertThat(((BeverageDelivery) result).quantity())
			.isEqualTo(quantity);
	}

	// --- Construction: invalid cases ---

	@ParameterizedTest
	@ValueSource(ints = {0, -1, -100, 10_001, Integer.MAX_VALUE})
	void parse_returnsInvalidBeverageDelivery_forInvalidQuantities(int quantity) {
		ParsedBeverageDelivery result = BeverageDelivery.parse("productName", quantity);

		assertThat(result).isInstanceOf(ParsedBeverageDelivery.Invalid.class);
		assertThat(((ParsedBeverageDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " "})
	void parse_returnsInvalidBeverageDelivery_forBlankProductName(String productName) {
		ParsedBeverageDelivery result = BeverageDelivery.parse(productName, 42);

		assertThat(result).isInstanceOf(ParsedBeverageDelivery.Invalid.class);
		assertThat(((ParsedBeverageDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@Test
	void parse_returnsInvalidBeverageDelivery_forNullProductName() {
		ParsedBeverageDelivery result = BeverageDelivery.parse(null, 42);

		assertThat(result).isInstanceOf(ParsedBeverageDelivery.Invalid.class);
		assertThat(((ParsedBeverageDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@Test
	void constructor_throws_forInvalidQuantity() {
		assertThatThrownBy(() -> new BeverageDelivery("productName", -5))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("must be greater than or equal to 1");
	}
}
