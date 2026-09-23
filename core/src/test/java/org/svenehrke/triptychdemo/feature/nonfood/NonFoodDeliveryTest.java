package org.svenehrke.triptychdemo.feature.nonfood;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NonFoodDeliveryTest {

	// --- Construction: valid cases ---

	@ParameterizedTest
	@ValueSource(ints = {1, 500, 10_000})
	void parse_returnsValidNonFoodDelivery_forValidQuantities(int quantity) {
		ParsedNonFoodDelivery result = NonFoodDelivery.parse("productName", quantity);

		assertThat(result).isInstanceOf(NonFoodDelivery.class);
		assertThat(((NonFoodDelivery) result).quantity())
			.isEqualTo(quantity);
	}

	// --- Construction: invalid cases ---

	@ParameterizedTest
	@ValueSource(ints = {0, -1, -100, 10_001, Integer.MAX_VALUE})
	void parse_returnsInvalidNonFoodDelivery_forInvalidQuantities(int quantity) {
		ParsedNonFoodDelivery result = NonFoodDelivery.parse("productName", quantity);

		assertThat(result).isInstanceOf(ParsedNonFoodDelivery.Invalid.class);
		assertThat(((ParsedNonFoodDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " "})
	void parse_returnsInvalidNonFoodDelivery_forBlankProductName(String productName) {
		ParsedNonFoodDelivery result = NonFoodDelivery.parse(productName, 42);

		assertThat(result).isInstanceOf(ParsedNonFoodDelivery.Invalid.class);
		assertThat(((ParsedNonFoodDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@Test
	void parse_returnsInvalidNonFoodDelivery_forNullProductName() {
		ParsedNonFoodDelivery result = NonFoodDelivery.parse(null, 42);

		assertThat(result).isInstanceOf(ParsedNonFoodDelivery.Invalid.class);
		assertThat(((ParsedNonFoodDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@Test
	void constructor_throws_forInvalidQuantity() {
		assertThatThrownBy(() -> new NonFoodDelivery("productName", -5))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("must be greater than or equal to 1");
	}
}
