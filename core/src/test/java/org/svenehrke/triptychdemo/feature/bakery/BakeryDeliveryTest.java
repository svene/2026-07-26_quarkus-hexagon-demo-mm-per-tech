package org.svenehrke.triptychdemo.feature.bakery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.junit.jupiter.api.Nested;
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

	@Nested
	class BakeryDeliveryJacksonTest {

		private final ObjectMapper mapper = new ObjectMapper();

		@Test
		void serializesInterfaceTypedInstance() throws Exception {
			BakeryDelivery delivery = new BakeryDelivery("productName", 42);

			String json = mapper.writeValueAsString(delivery);

			assertThat(json).isEqualTo("""
				{"productName":"productName","quantity":42}\
				""");
		}

		@Test
		void deserializesToInterfaceType() throws Exception {
			BakeryDelivery delivery = mapper.readValue("{\"productName\":\"productName\",\"quantity\":42}", BakeryDelivery.class);

			assertThat(delivery.quantity()).isEqualTo(42);
			assertThat(delivery).isInstanceOf(BakeryDelivery.class);
		}

		@Test
		void deserializationFailsForInvalidQuantity() {
			assertThatThrownBy(() -> mapper.readValue("{\"productName\":\"productName\",\"quantity\":-5}", BakeryDelivery.class))
				.isInstanceOf(ValueInstantiationException.class)
				.hasCauseInstanceOf(IllegalArgumentException.class)
				.cause()
				.hasMessage("must be greater than or equal to 1");
		}

		@Test
		void deserializationFailsForBlankProductName() {
			assertThatThrownBy(() -> mapper.readValue("{\"quantity\":42}", BakeryDelivery.class))
				.isInstanceOf(ValueInstantiationException.class)
				.hasCauseInstanceOf(IllegalArgumentException.class)
				.cause()
				.hasMessage("must not be blank");
		}
	}
}
