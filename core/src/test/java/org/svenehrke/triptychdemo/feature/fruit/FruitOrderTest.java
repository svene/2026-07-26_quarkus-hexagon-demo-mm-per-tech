package org.svenehrke.triptychdemo.feature.fruit;

import jakarta.validation.ConstraintViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class FruitOrderTest {

	// --- parse(String, String): text input, e.g. an HTML form field ---

	@ParameterizedTest
	@ValueSource(strings = {"1", "42", " 42 ", "+7", "999999999"})
	void parseText_returnsValidFruitOrder_forNumericQuantities(String quantity) {
		ParsedFruitOrder result = FruitOrder.parse("productName", quantity);

		assertThat(result).isInstanceOf(FruitOrder.class);
		assertThat(((FruitOrder) result).quantity()).isEqualTo(Integer.parseInt(quantity.trim()));
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "1.5", "12abc", "1 2", "1234567890"})
	void parseText_returnsInvalid_forNonNumericQuantities(String quantity) {
		ParsedFruitOrder result = FruitOrder.parse("productName", quantity);

		assertThat(messages(result)).containsExactly("must be a number");
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " "})
	void parseText_returnsInvalid_forBlankQuantity(String quantity) {
		ParsedFruitOrder result = FruitOrder.parse("productName", quantity);

		assertThat(messages(result)).containsExactly("must not be blank");
	}

	@Test
	void parseText_returnsInvalid_forNullQuantity() {
		ParsedFruitOrder result = FruitOrder.parse("productName", (String) null);

		assertThat(messages(result)).containsExactly("must not be blank");
	}

	@Test
	void parseText_appliesIntConstraints_afterConversion() {
		ParsedFruitOrder result = FruitOrder.parse("productName", "0");

		assertThat(messages(result)).containsExactly("must be greater than or equal to 1");
	}

	@Test
	void parseText_reportsBlankProductNameAndNonNumericQuantityTogether() {
		ParsedFruitOrder result = FruitOrder.parse(" ", "abc");

		assertThat(messages(result)).containsExactlyInAnyOrder("must not be blank", "must be a number");
	}

	private static java.util.List<String> messages(ParsedFruitOrder result) {
		assertThat(result).isInstanceOf(ParsedFruitOrder.Invalid.class);
		return ((ParsedFruitOrder.Invalid) result).violations().stream().map(ConstraintViolation::getMessage).toList();
	}
}
