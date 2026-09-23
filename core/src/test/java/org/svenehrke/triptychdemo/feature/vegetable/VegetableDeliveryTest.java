package org.svenehrke.triptychdemo.feature.vegetable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VegetableDeliveryTest {

	// --- Construction: valid cases ---

	@ParameterizedTest
	@ValueSource(ints = {1, 500, 10_000})
	void parse_returnsValidVegetableDelivery_forValidQuantities(int quantity) {
		ParsedVegetableDelivery result = VegetableDelivery.parse("productName", quantity);

		assertThat(result).isInstanceOf(VegetableDelivery.class);
		assertThat(((VegetableDelivery) result).quantity())
			.isEqualTo(quantity);
	}

	// --- Construction: invalid cases ---

	@ParameterizedTest
	@ValueSource(ints = {0, -1, -100, 10_001, Integer.MAX_VALUE})
	void parse_returnsInvalidVegetableDelivery_forInvalidQuantities(int quantity) {
		ParsedVegetableDelivery result = VegetableDelivery.parse("productName", quantity);

		assertThat(result).isInstanceOf(ParsedVegetableDelivery.Invalid.class);
		assertThat(((ParsedVegetableDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " "})
	void parse_returnsInvalidVegetableDelivery_forBlankProductName(String productName) {
		ParsedVegetableDelivery result = VegetableDelivery.parse(productName, 42);

		assertThat(result).isInstanceOf(ParsedVegetableDelivery.Invalid.class);
		assertThat(((ParsedVegetableDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@Test
	void parse_returnsInvalidVegetableDelivery_forNullProductName() {
		ParsedVegetableDelivery result = VegetableDelivery.parse(null, 42);

		assertThat(result).isInstanceOf(ParsedVegetableDelivery.Invalid.class);
		assertThat(((ParsedVegetableDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@Nested
	class VegetableDeliveryJacksonTest {

		private final ObjectMapper mapper = new ObjectMapper();

		@Test
		void serializesInterfaceTypedInstance() throws Exception {
			VegetableDelivery delivery = new VegetableDelivery("productName", 42);

			String json = mapper.writeValueAsString(delivery);

			assertThat(json).isEqualTo("""
				{"productName":"productName","quantity":42}\
				""");
		}

		@Test
		void deserializesToInterfaceType() throws Exception {
			VegetableDelivery delivery = mapper.readValue("{\"productName\":\"productName\",\"quantity\":42}", VegetableDelivery.class);

			assertThat(delivery.quantity()).isEqualTo(42);
			assertThat(delivery).isInstanceOf(VegetableDelivery.class);
		}

		@Test
		void deserializationFailsForInvalidQuantity() {
			assertThatThrownBy(() -> mapper.readValue("{\"productName\":\"productName\",\"quantity\":-5}", VegetableDelivery.class))
				.isInstanceOf(ValueInstantiationException.class)
				.hasCauseInstanceOf(IllegalArgumentException.class)
				.cause()
				.hasMessage("must be greater than or equal to 1");
		}

		@Test
		void deserializationFailsForBlankProductName() {
			assertThatThrownBy(() -> mapper.readValue("{\"quantity\":42}", VegetableDelivery.class))
				.isInstanceOf(ValueInstantiationException.class)
				.hasCauseInstanceOf(IllegalArgumentException.class)
				.cause()
				.hasMessage("must not be blank");
		}
	}
}
