package org.svenehrke.triptychdemo.feature.beverage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.junit.jupiter.api.Nested;
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

	@Nested
	class BeverageDeliveryJacksonTest {

		private final ObjectMapper mapper = new ObjectMapper();

		@Test
		void serializesInterfaceTypedInstance() throws Exception {
			BeverageDelivery delivery = new BeverageDelivery("productName", 42);

			String json = mapper.writeValueAsString(delivery);

			assertThat(json).isEqualTo("""
				{"productName":"productName","quantity":42}\
				""");
		}

		@Test
		void deserializesToInterfaceType() throws Exception {
			BeverageDelivery delivery = mapper.readValue("{\"productName\":\"productName\",\"quantity\":42}", BeverageDelivery.class);

			assertThat(delivery.quantity()).isEqualTo(42);
			assertThat(delivery).isInstanceOf(BeverageDelivery.class);
		}

		@Test
		void deserializationFailsForInvalidQuantity() {
			assertThatThrownBy(() -> mapper.readValue("{\"productName\":\"productName\",\"quantity\":-5}", BeverageDelivery.class))
				.isInstanceOf(ValueInstantiationException.class)
				.hasCauseInstanceOf(IllegalArgumentException.class)
				.cause()
				.hasMessage("must be greater than or equal to 1");
		}

		@Test
		void deserializationFailsForBlankProductName() {
			assertThatThrownBy(() -> mapper.readValue("{\"quantity\":42}", BeverageDelivery.class))
				.isInstanceOf(ValueInstantiationException.class)
				.hasCauseInstanceOf(IllegalArgumentException.class)
				.cause()
				.hasMessage("must not be blank");
		}
	}
}
