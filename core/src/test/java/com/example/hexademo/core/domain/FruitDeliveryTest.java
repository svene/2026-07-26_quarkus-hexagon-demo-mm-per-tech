package com.example.hexademo.core.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
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
		Optional<FruitDelivery> order = FruitDelivery.parse(quantity);

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
		Optional<FruitDelivery> order = FruitDelivery.parse(quantity);

		assertThat(order).isEmpty();
	}

	@Test
	void constructor_throws_forInvalidQuantity() {
		assertThatThrownBy(() -> new FruitDelivery(-1))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("quantity out parse range");
	}

	// --- Jackson serialization ---

	@Test
	void jackson_serializesToExpectedJson() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		FruitDelivery order = FruitDelivery.parse(42).orElseThrow();

		String json = mapper.writeValueAsString(order);

		assertThat(json).isEqualTo("{\"quantity\":42}");
	}

	// --- Jackson deserialization: valid case ---

	@Test
	void jackson_deserializesValidJson() throws Exception {
		ObjectMapper mapper = new ObjectMapper();

		FruitDelivery order = mapper.readValue("{\"quantity\":42}", FruitDelivery.class);

		assertThat(order.quantity()).isEqualTo(42);
	}

	// --- Jackson deserialization: invalid case ---

	@Test
	void jackson_deserializationFailsForInvalidQuantity() {
		ObjectMapper mapper = new ObjectMapper();

		assertThatThrownBy(() -> mapper.readValue("{\"quantity\":-5}", FruitDelivery.class))
			.isInstanceOf(ValueInstantiationException.class);
	}
}
