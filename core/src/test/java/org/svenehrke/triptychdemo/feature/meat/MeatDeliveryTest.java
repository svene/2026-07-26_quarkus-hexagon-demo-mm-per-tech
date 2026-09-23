package org.svenehrke.triptychdemo.feature.meat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeatDeliveryTest {

	// --- Construction: valid cases ---

	@ParameterizedTest
	@ValueSource(ints = {1, 500, 10_000})
	void parse_returnsValidMeatDelivery_forValidQuantities(int quantity) {
		ParsedMeatDelivery result = MeatDelivery.parse("productName", quantity);

		assertThat(result).isInstanceOf(MeatDelivery.class);
		assertThat(((MeatDelivery) result).quantity())
			.isEqualTo(quantity);
	}

	// --- Construction: invalid cases ---

	@ParameterizedTest
	@ValueSource(ints = {0, -1, -100, 10_001, Integer.MAX_VALUE})
	void parse_returnsInvalidMeatDelivery_forInvalidQuantities(int quantity) {
		ParsedMeatDelivery result = MeatDelivery.parse("productName", quantity);

		assertThat(result).isInstanceOf(ParsedMeatDelivery.Invalid.class);
		assertThat(((ParsedMeatDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " "})
	void parse_returnsInvalidMeatDelivery_forBlankProductName(String productName) {
		ParsedMeatDelivery result = MeatDelivery.parse(productName, 42);

		assertThat(result).isInstanceOf(ParsedMeatDelivery.Invalid.class);
		assertThat(((ParsedMeatDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@Test
	void parse_returnsInvalidMeatDelivery_forNullProductName() {
		ParsedMeatDelivery result = MeatDelivery.parse(null, 42);

		assertThat(result).isInstanceOf(ParsedMeatDelivery.Invalid.class);
		assertThat(((ParsedMeatDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@Test
	void constructor_throws_forInvalidQuantity() {
		assertThatThrownBy(() -> new MeatDelivery("productName", -5))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("must be greater than or equal to 1");
	}
}
