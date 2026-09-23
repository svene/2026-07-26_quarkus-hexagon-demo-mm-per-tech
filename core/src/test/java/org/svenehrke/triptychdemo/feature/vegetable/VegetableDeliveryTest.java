package org.svenehrke.triptychdemo.feature.vegetable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VegetableDeliveryTest {

	// --- Construction: valid cases ---

	@ParameterizedTest
	@ValueSource(ints = {1, 500, 10_000})
	void parse_returnsValidVegetableDelivery_forValidQuantities(int quantity) {
		ParsedVegetableDelivery result = VegetableDelivery.parse("productName", quantity);

		assertThat(result).isInstanceOf(VegetableDelivery.class);
		assertThat(((VegetableDelivery) result).quantity())
			.isEqualTo(quantity);
	}

	// --- Construction: invalid cases ---

	@ParameterizedTest
	@ValueSource(ints = {0, -1, -100, 10_001, Integer.MAX_VALUE})
	void parse_returnsInvalidVegetableDelivery_forInvalidQuantities(int quantity) {
		ParsedVegetableDelivery result = VegetableDelivery.parse("productName", quantity);

		assertThat(result).isInstanceOf(ParsedVegetableDelivery.Invalid.class);
		assertThat(((ParsedVegetableDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " "})
	void parse_returnsInvalidVegetableDelivery_forBlankProductName(String productName) {
		ParsedVegetableDelivery result = VegetableDelivery.parse(productName, 42);

		assertThat(result).isInstanceOf(ParsedVegetableDelivery.Invalid.class);
		assertThat(((ParsedVegetableDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@Test
	void parse_returnsInvalidVegetableDelivery_forNullProductName() {
		ParsedVegetableDelivery result = VegetableDelivery.parse(null, 42);

		assertThat(result).isInstanceOf(ParsedVegetableDelivery.Invalid.class);
		assertThat(((ParsedVegetableDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@Test
	void constructor_throws_forInvalidQuantity() {
		assertThatThrownBy(() -> new VegetableDelivery("productName", -5))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("must be greater than or equal to 1");
	}
}
