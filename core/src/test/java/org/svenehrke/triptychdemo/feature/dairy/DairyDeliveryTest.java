package org.svenehrke.triptychdemo.feature.dairy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DairyDeliveryTest {

	// --- Construction: valid cases ---

	@ParameterizedTest
	@ValueSource(ints = {1, 500, 10_000})
	void parse_returnsValidDairyDelivery_forValidQuantities(int quantity) {
		ParsedDairyDelivery result = DairyDelivery.parse("productName", quantity);

		assertThat(result).isInstanceOf(DairyDelivery.class);
		assertThat(((DairyDelivery) result).quantity())
			.isEqualTo(quantity);
	}

	// --- Construction: invalid cases ---

	@ParameterizedTest
	@ValueSource(ints = {0, -1, -100, 10_001, Integer.MAX_VALUE})
	void parse_returnsInvalidDairyDelivery_forInvalidQuantities(int quantity) {
		ParsedDairyDelivery result = DairyDelivery.parse("productName", quantity);

		assertThat(result).isInstanceOf(ParsedDairyDelivery.Invalid.class);
		assertThat(((ParsedDairyDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " "})
	void parse_returnsInvalidDairyDelivery_forBlankProductName(String productName) {
		ParsedDairyDelivery result = DairyDelivery.parse(productName, 42);

		assertThat(result).isInstanceOf(ParsedDairyDelivery.Invalid.class);
		assertThat(((ParsedDairyDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@Test
	void parse_returnsInvalidDairyDelivery_forNullProductName() {
		ParsedDairyDelivery result = DairyDelivery.parse(null, 42);

		assertThat(result).isInstanceOf(ParsedDairyDelivery.Invalid.class);
		assertThat(((ParsedDairyDelivery.Invalid) result).violations()).isNotEmpty();
	}

	@Nested
	class DairyDeliveryJacksonTest {

		private final ObjectMapper mapper = new ObjectMapper();

		@Test
		void serializesInterfaceTypedInstance() throws Exception {
			DairyDelivery delivery = new DairyDelivery("productName", 42);

			String json = mapper.writeValueAsString(delivery);

			assertThat(json).isEqualTo("""
				{"productName":"productName","quantity":42}\
				""");
		}

		@Test
		void deserializesToInterfaceType() throws Exception {
			DairyDelivery delivery = mapper.readValue("{\"productName\":\"productName\",\"quantity\":42}", DairyDelivery.class);

			assertThat(delivery.quantity()).isEqualTo(42);
			assertThat(delivery).isInstanceOf(DairyDelivery.class);
		}

		@Test
		void deserializationFailsForInvalidQuantity() {
			assertThatThrownBy(() -> mapper.readValue("{\"productName\":\"productName\",\"quantity\":-5}", DairyDelivery.class))
				.isInstanceOf(ValueInstantiationException.class)
				.hasCauseInstanceOf(IllegalArgumentException.class)
				.cause()
				.hasMessage("must be greater than or equal to 1");
		}

		@Test
		void deserializationFailsForBlankProductName() {
			assertThatThrownBy(() -> mapper.readValue("{\"quantity\":42}", DairyDelivery.class))
				.isInstanceOf(ValueInstantiationException.class)
				.hasCauseInstanceOf(IllegalArgumentException.class)
				.cause()
				.hasMessage("must not be blank");
		}
	}
}
