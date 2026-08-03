package com.example.hexademo.core.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FruitDeliveryTest {

	// --- Construction: valid cases ---

	@ParameterizedTest
	@ValueSource(ints = {1, 500, 10_000})
	void parse_returnsPresentOptional_forValidQuantities(int quantity) {
		Optional<FruitDelivery> order = FruitDelivery.parse("productName", quantity);

		assertThat(order)
			.isPresent()
			.get()
			.extracting(FruitDelivery::quantity)
			.isEqualTo(quantity);
	}

	// --- Construction: invalid cases ---

	@ParameterizedTest
	@ValueSource(ints = {0, -1, -100, 10_001, Integer.MAX_VALUE})
	void parse_returnsEmptyOptional_forInvalidQuantities(int quantity) {
		Optional<FruitDelivery> order = FruitDelivery.parse("productName", quantity);

		assertThat(order).isEmpty();
	}

	@Nested
	class FruitDeliveryJacksonTest {

		private final ObjectMapper mapper = new ObjectMapper();

		@Test
		void serializesInterfaceTypedInstance() throws Exception {
			FruitDelivery delivery = FruitDelivery.parse("productName", 42).orElseThrow();

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
				.isInstanceOf(ValueInstantiationException.class);
		}
	}
}
