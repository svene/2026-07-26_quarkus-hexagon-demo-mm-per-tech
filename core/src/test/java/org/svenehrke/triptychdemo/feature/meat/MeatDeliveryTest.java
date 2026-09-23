package org.svenehrke.triptychdemo.feature.meat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeatDeliveryTest {

	// --- Construction: valid cases ---

	@ParameterizedTest
	@ValueSource(ints = {1, 500, 10_000})
	void parse_returnsValidMeatDelivery_forValidQuantities(int quantity) {
		ParsedMeatDelivery result = MeatDelivery.parse("productName", quantity);

		assertThat(result).isInstanceOf(MeatDelivery.class);
		assertThat(((MeatDelivery) result).quantity())
			.isEqualTo(quantity);
	}

	// --- Construction: invalid cases ---

	@ParameterizedTest
	@ValueSource(ints = {0, -1, -100, 10_001, Integer.MAX_VALUE})
	void parse_returnsInvalidMeatDelivery_forInvalidQuantities(int quantity) {
		ParsedMeatDelivery result = MeatDelivery.parse("productName", quantity);

		assertThat(result).isInstanceOf(ParsedMeatDelivery.Invalid.class);
		assertThat(((ParsedMeatDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " "})
	void parse_returnsInvalidMeatDelivery_forBlankProductName(String productName) {
		ParsedMeatDelivery result = MeatDelivery.parse(productName, 42);

		assertThat(result).isInstanceOf(ParsedMeatDelivery.Invalid.class);
		assertThat(((ParsedMeatDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@Test
	void parse_returnsInvalidMeatDelivery_forNullProductName() {
		ParsedMeatDelivery result = MeatDelivery.parse(null, 42);

		assertThat(result).isInstanceOf(ParsedMeatDelivery.Invalid.class);
		assertThat(((ParsedMeatDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@Nested
	class MeatDeliveryJacksonTest {

		private final ObjectMapper mapper = new ObjectMapper();

		@Test
		void serializesInterfaceTypedInstance() throws Exception {
			MeatDelivery delivery = new MeatDelivery("productName", 42);

			String json = mapper.writeValueAsString(delivery);

			assertThat(json).isEqualTo("""
				{"productName":"productName","quantity":42}\
				""");
		}

		@Test
		void deserializesToInterfaceType() throws Exception {
			MeatDelivery delivery = mapper.readValue("{\"productName\":\"productName\",\"quantity\":42}", MeatDelivery.class);

			assertThat(delivery.quantity()).isEqualTo(42);
			assertThat(delivery).isInstanceOf(MeatDelivery.class);
		}

		@Test
		void deserializationFailsForInvalidQuantity() {
			assertThatThrownBy(() -> mapper.readValue("{\"productName\":\"productName\",\"quantity\":-5}", MeatDelivery.class))
				.isInstanceOf(ValueInstantiationException.class)
				.hasCauseInstanceOf(IllegalArgumentException.class)
				.cause()
				.hasMessage("must be greater than or equal to 1");
		}

		@Test
		void deserializationFailsForBlankProductName() {
			assertThatThrownBy(() -> mapper.readValue("{\"quantity\":42}", MeatDelivery.class))
				.isInstanceOf(ValueInstantiationException.class)
				.hasCauseInstanceOf(IllegalArgumentException.class)
				.cause()
				.hasMessage("must not be blank");
		}
	}
}
