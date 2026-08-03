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

class NonFoodDeliveryTest {

	// --- Construction: valid cases ---

	@ParameterizedTest
	@ValueSource(ints = {1, 500, 10_000})
	void parse_returnsPresentOptional_forValidQuantities(int quantity) {
		Optional<NonFoodDelivery> order = NonFoodDelivery.parse("productName", quantity);

		assertThat(order)
			.isPresent()
			.get()
			.extracting(NonFoodDelivery::quantity)
			.isEqualTo(quantity);
	}

	// --- Construction: invalid cases ---

	@ParameterizedTest
	@ValueSource(ints = {0, -1, -100, 10_001, Integer.MAX_VALUE})
	void parse_returnsEmptyOptional_forInvalidQuantities(int quantity) {
		Optional<NonFoodDelivery> order = NonFoodDelivery.parse("productName", quantity);

		assertThat(order).isEmpty();
	}

	@Nested
	class NonFoodDeliveryJacksonTest {

		private final ObjectMapper mapper = new ObjectMapper();

		@Test
		void serializesInterfaceTypedInstance() throws Exception {
			NonFoodDelivery delivery = NonFoodDelivery.parse("productName", 42).orElseThrow();

			String json = mapper.writeValueAsString(delivery);

			assertThat(json).isEqualTo("""
				{"productName":"productName","quantity":42}\
				""");
		}

		@Test
		void deserializesToInterfaceType() throws Exception {
			NonFoodDelivery delivery = mapper.readValue("{\"quantity\":42}", NonFoodDelivery.class);

			assertThat(delivery.quantity()).isEqualTo(42);
			assertThat(delivery).isInstanceOf(NonFoodDelivery.class);
		}

		@Test
		void deserializationFailsForInvalidQuantity() {
			assertThatThrownBy(() -> mapper.readValue("{\"quantity\":-5}", NonFoodDelivery.class))
				.isInstanceOf(ValueInstantiationException.class);
		}
	}
}
