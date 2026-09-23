package org.svenehrke.triptychdemo.cross.purchase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseTest {

	// --- PurchaseItem ---

	@Test
	void parseItem_returnsValidPurchaseItem_forValidInput() {
		assertThat(PurchaseItem.parse("Apple", 3)).isEqualTo(new PurchaseItem("Apple", 3));
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -1, -100})
	void parseItem_returnsInvalid_forNonPositiveQuantity(int quantity) {
		assertThat(PurchaseItem.parse("Apple", quantity)).isInstanceOf(ParsedPurchaseItem.Invalid.class);
	}

	@Test
	void parseItem_returnsInvalid_forBlankProductName() {
		assertThat(PurchaseItem.parse(" ", 3)).isInstanceOf(ParsedPurchaseItem.Invalid.class);
	}

	@Test
	void itemConstructor_throws_forInvalidQuantity() {
		assertThatThrownBy(() -> new PurchaseItem("Apple", -5))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("must be greater than or equal to 1");
	}

	// --- Purchase (all-or-nothing) ---

	@Test
	void parse_returnsPurchase_whenAllItemsAreValid() {
		ParsedPurchase result = Purchase.parse(List.of(
			PurchaseItem.parse("Apple", 3),
			PurchaseItem.parse("Milk", 2)
		));

		assertThat(result).isEqualTo(new Purchase(List.of(new PurchaseItem("Apple", 3), new PurchaseItem("Milk", 2))));
	}

	@Test
	void parse_returnsInvalid_withOffendingItemIndex_whenAnyItemIsInvalid() {
		ParsedPurchase result = Purchase.parse(List.of(
			PurchaseItem.parse("Apple", 3),
			PurchaseItem.parse("Milk", -2)
		));

		assertThat(result).isInstanceOf(ParsedPurchase.Invalid.class);
		var invalid = (ParsedPurchase.Invalid) result;
		assertThat(invalid.violationsByItemIndex()).containsOnlyKeys(1);
		assertThat(invalid.messages()).containsExactly("items[1]: must be greater than or equal to 1");
	}

	@Test
	void parse_returnsEmptyPurchase_forNoItems() {
		assertThat(Purchase.parse(List.of())).isEqualTo(new Purchase(List.of()));
	}
}
