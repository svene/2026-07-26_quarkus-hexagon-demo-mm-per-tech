package org.svenehrke.triptychdemo.feature.bakery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BakeryDeliveryTest {

	// --- Construction: valid cases ---

	@ParameterizedTest
	@ValueSource(ints = {1, 500, 10_000})
	void parse_returnsValidBakeryDelivery_forValidQuantities(int quantity) {
		ParsedBakeryDelivery result = BakeryDelivery.parse("productName", quantity);

		assertThat(result).isInstanceOf(BakeryDelivery.class);
		assertThat(((BakeryDelivery) result).quantity())
			.isEqualTo(quantity);
	}

	// --- Construction: invalid cases ---

	@ParameterizedTest
	@ValueSource(ints = {0, -1, -100, 10_001, Integer.MAX_VALUE})
	void parse_returnsInvalidBakeryDelivery_forInvalidQuantities(int quantity) {
		ParsedBakeryDelivery result = BakeryDelivery.parse("productName", quantity);

		assertThat(result).isInstanceOf(ParsedBakeryDelivery.Invalid.class);
		assertThat(((ParsedBakeryDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " "})
	void parse_returnsInvalidBakeryDelivery_forBlankProductName(String productName) {
		ParsedBakeryDelivery result = BakeryDelivery.parse(productName, 42);

		assertThat(result).isInstanceOf(ParsedBakeryDelivery.Invalid.class);
		assertThat(((ParsedBakeryDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@Test
	void parse_returnsInvalidBakeryDelivery_forNullProductName() {
		ParsedBakeryDelivery result = BakeryDelivery.parse(null, 42);

		assertThat(result).isInstanceOf(ParsedBakeryDelivery.Invalid.class);
		assertThat(((ParsedBakeryDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@Test
	void constructor_throws_forInvalidQuantity() {
		assertThatThrownBy(() -> new BakeryDelivery("productName", -5))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("must be greater than or equal to 1");
	}
}
