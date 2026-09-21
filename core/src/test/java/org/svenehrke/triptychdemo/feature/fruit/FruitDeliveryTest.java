package org.svenehrke.triptychdemo.feature.fruit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.junit.jupiter.api.Nested;
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

	@Nested
	class FruitDeliveryJacksonTest {

		private final ObjectMapper mapper = new ObjectMapper();

		@Test
		void serializesInterfaceTypedInstance() throws Exception {
			FruitDelivery delivery = new FruitDelivery("productName", 42);

			String json = mapper.writeValueAsString(delivery);

			assertThat(json).isEqualTo("""
				{"productName":"productName","quantity":42}\
				""");
		}

		@Test
		void deserializesToInterfaceType() throws Exception {
			FruitDelivery delivery = mapper.readValue("{\"quantity\":42}", FruitDelivery.class);

			assertThat(delivery.quantity()).isEqualTo(42);
			assertThat(delivery).isInstanceOf(FruitDelivery.class);
		}

		@Test
		void deserializationFailsForInvalidQuantity() {
			assertThatThrownBy(() -> mapper.readValue("{\"quantity\":-5}", FruitDelivery.class))
				.isInstanceOf(ValueInstantiationException.class)
				.hasCauseInstanceOf(IllegalArgumentException.class)
				.cause()
				.hasMessage("must be greater than or equal to 1");
		}
	}
}
