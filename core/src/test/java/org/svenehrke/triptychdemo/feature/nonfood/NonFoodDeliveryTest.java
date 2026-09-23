package org.svenehrke.triptychdemo.feature.nonfood;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.junit.jupiter.api.Nested;
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

	@Nested
	class NonFoodDeliveryJacksonTest {

		private final ObjectMapper mapper = new ObjectMapper();

		@Test
		void serializesInterfaceTypedInstance() throws Exception {
			NonFoodDelivery delivery = new NonFoodDelivery("productName", 42);

			String json = mapper.writeValueAsString(delivery);

			assertThat(json).isEqualTo("""
				{"productName":"productName","quantity":42}\
				""");
		}

		@Test
		void deserializesToInterfaceType() throws Exception {
			NonFoodDelivery delivery = mapper.readValue("{\"productName\":\"productName\",\"quantity\":42}", NonFoodDelivery.class);

			assertThat(delivery.quantity()).isEqualTo(42);
			assertThat(delivery).isInstanceOf(NonFoodDelivery.class);
		}

		@Test
		void deserializationFailsForInvalidQuantity() {
			assertThatThrownBy(() -> mapper.readValue("{\"productName\":\"productName\",\"quantity\":-5}", NonFoodDelivery.class))
				.isInstanceOf(ValueInstantiationException.class)
				.hasCauseInstanceOf(IllegalArgumentException.class)
				.cause()
				.hasMessage("must be greater than or equal to 1");
		}

		@Test
		void deserializationFailsForBlankProductName() {
			assertThatThrownBy(() -> mapper.readValue("{\"quantity\":42}", NonFoodDelivery.class))
				.isInstanceOf(ValueInstantiationException.class)
				.hasCauseInstanceOf(IllegalArgumentException.class)
				.cause()
				.hasMessage("must not be blank");
		}
	}
}
