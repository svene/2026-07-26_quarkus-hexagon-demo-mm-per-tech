package org.svenehrke.triptychdemo.feature.fruit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FruitDeliveryTest {

	// --- Construction: valid cases ---

	@ParameterizedTest
	@ValueSource(ints = {1, 500, 10_000})
	void parse_returnsValidFruitDelivery_forValidQuantities(int quantity) {
		ParsedFruitDelivery result = FruitDelivery.parse("productName", quantity);

		assertThat(result).isInstanceOf(FruitDelivery.class);
		assertThat(((FruitDelivery) result).quantity())
			.isEqualTo(quantity);
	}

	// --- Construction: invalid cases ---

	@ParameterizedTest
	@ValueSource(ints = {0, -1, -100, 10_001, Integer.MAX_VALUE})
	void parse_returnsInvalidFruitDelivery_forInvalidQuantities(int quantity) {
		ParsedFruitDelivery result = FruitDelivery.parse("productName", quantity);

		assertThat(result).isInstanceOf(ParsedFruitDelivery.Invalid.class);
		assertThat(((ParsedFruitDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " "})
	void parse_returnsInvalidFruitDelivery_forBlankProductName(String productName) {
		ParsedFruitDelivery result = FruitDelivery.parse(productName, 42);

		assertThat(result).isInstanceOf(ParsedFruitDelivery.Invalid.class);
		assertThat(((ParsedFruitDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@Test
	void parse_returnsInvalidFruitDelivery_forNullProductName() {
		ParsedFruitDelivery result = FruitDelivery.parse(null, 42);

		assertThat(result).isInstanceOf(ParsedFruitDelivery.Invalid.class);
		assertThat(((ParsedFruitDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@Test
	void constructor_throws_forInvalidQuantity() {
		assertThatThrownBy(() -> new FruitDelivery("productName", -5))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("must be greater than or equal to 1");
	}
}
