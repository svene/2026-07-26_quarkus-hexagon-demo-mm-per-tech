package org.svenehrke.triptychdemo.feature.dairy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DairyDeliveryTest {

	// --- Construction: valid cases ---

	@ParameterizedTest
	@ValueSource(ints = {1, 500, 10_000})
	void parse_returnsValidDairyDelivery_forValidQuantities(int quantity) {
		ParsedDairyDelivery result = DairyDelivery.parse("productName", quantity);

		assertThat(result).isInstanceOf(DairyDelivery.class);
		assertThat(((DairyDelivery) result).quantity())
			.isEqualTo(quantity);
	}

	// --- Construction: invalid cases ---

	@ParameterizedTest
	@ValueSource(ints = {0, -1, -100, 10_001, Integer.MAX_VALUE})
	void parse_returnsInvalidDairyDelivery_forInvalidQuantities(int quantity) {
		ParsedDairyDelivery result = DairyDelivery.parse("productName", quantity);

		assertThat(result).isInstanceOf(ParsedDairyDelivery.Invalid.class);
		assertThat(((ParsedDairyDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " "})
	void parse_returnsInvalidDairyDelivery_forBlankProductName(String productName) {
		ParsedDairyDelivery result = DairyDelivery.parse(productName, 42);

		assertThat(result).isInstanceOf(ParsedDairyDelivery.Invalid.class);
		assertThat(((ParsedDairyDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@Test
	void parse_returnsInvalidDairyDelivery_forNullProductName() {
		ParsedDairyDelivery result = DairyDelivery.parse(null, 42);

		assertThat(result).isInstanceOf(ParsedDairyDelivery.Invalid.class);
		assertThat(((ParsedDairyDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@Test
	void constructor_throws_forInvalidQuantity() {
		assertThatThrownBy(() -> new DairyDelivery("productName", -5))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("must be greater than or equal to 1");
	}
}
