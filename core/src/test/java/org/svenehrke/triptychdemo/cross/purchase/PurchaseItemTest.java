package org.svenehrke.triptychdemo.cross.purchase;

import jakarta.validation.ConstraintViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseItemTest {

	// --- parse(String, String): text input, e.g. an HTML form field ---

	@ParameterizedTest
	@ValueSource(strings = {"1", "42", " 42 ", "+7", "50"})
	void parseText_returnsValidPurchaseItem_forNumericQuantities(String quantity) {
		ParsedPurchaseItem result = PurchaseItem.parse("productName", quantity);

		assertThat(result).isInstanceOf(PurchaseItem.class);
		assertThat(((PurchaseItem) result).quantity()).isEqualTo(Integer.parseInt(quantity.trim()));
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "1.5", "12abc", "1 2", "1234567890"})
	void parseText_returnsInvalid_forNonNumericQuantities(String quantity) {
		ParsedPurchaseItem result = PurchaseItem.parse("productName", quantity);

		assertThat(messages(result)).containsExactly("must be a number");
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " "})
	void parseText_returnsInvalid_forBlankQuantity(String quantity) {
		ParsedPurchaseItem result = PurchaseItem.parse("productName", quantity);

		assertThat(messages(result)).containsExactly("must not be blank");
	}

	@Test
	void parseText_appliesIntConstraints_afterConversion() {
		ParsedPurchaseItem result = PurchaseItem.parse("productName", "-2");

		assertThat(messages(result)).containsExactly("must be greater than or equal to 1");
	}

	@ParameterizedTest
	@ValueSource(strings = {"51", "999999999"})
	void parseText_returnsInvalid_abovePurchaseLimit(String quantity) {
		ParsedPurchaseItem result = PurchaseItem.parse("productName", quantity);

		assertThat(messages(result)).containsExactly("must be less than or equal to 50");
	}

	private static java.util.List<String> messages(ParsedPurchaseItem result) {
		assertThat(result).isInstanceOf(ParsedPurchaseItem.Invalid.class);
		return ((ParsedPurchaseItem.Invalid) result).violations().stream().map(ConstraintViolation::getMessage).toList();
	}
}
