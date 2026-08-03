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

class VegetableDeliveryTest {

	// --- Construction: valid cases ---

	@ParameterizedTest
	@ValueSource(ints = {1, 500, 10_000})
	void parse_returnsPresentOptional_forValidQuantities(int quantity) {
		Optional<VegetableDelivery> order = VegetableDelivery.parse("productName", quantity);

		assertThat(order)
			.isPresent()
			.get()
			.extracting(VegetableDelivery::quantity)
			.isEqualTo(quantity);
	}

	// --- Construction: invalid cases ---

	@ParameterizedTest
	@ValueSource(ints = {0, -1, -100, 10_001, Integer.MAX_VALUE})
	void parse_returnsEmptyOptional_forInvalidQuantities(int quantity) {
		Optional<VegetableDelivery> order = VegetableDelivery.parse("productName", quantity);

		assertThat(order).isEmpty();
	}

	@Nested
	class VegetableDeliveryJacksonTest {

		private final ObjectMapper mapper = new ObjectMapper();

		@Test
		void serializesInterfaceTypedInstance() throws Exception {
			VegetableDelivery delivery = VegetableDelivery.parse("productName", 42).orElseThrow();

			String json = mapper.writeValueAsString(delivery);

			assertThat(json).isEqualTo("""
				{"productName":"productName","quantity":42}\
				""");
		}

		@Test
		void deserializesToInterfaceType() throws Exception {
			VegetableDelivery delivery = mapper.readValue("{\"quantity\":42}", VegetableDelivery.class);

			assertThat(delivery.quantity()).isEqualTo(42);
			assertThat(delivery).isInstanceOf(VegetableDelivery.class);
		}

		@Test
		void deserializationFailsForInvalidQuantity() {
			assertThatThrownBy(() -> mapper.readValue("{\"quantity\":-5}", VegetableDelivery.class))
				.isInstanceOf(ValueInstantiationException.class);
		}
	}
}
