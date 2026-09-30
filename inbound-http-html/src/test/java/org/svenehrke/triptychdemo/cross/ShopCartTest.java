package org.svenehrke.triptychdemo.cross;

import org.junit.jupiter.api.Test;
import org.svenehrke.triptychdemo.cross.purchase.ParsedPurchase;
import org.svenehrke.triptychdemo.cross.purchase.Purchase;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseItem;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShopCartTest {

	@Test
	void rows_skipBlankQuantities_butKeepZero() {
		var cart = ShopCart.of(List.of("Apple", "Milk", "Bread"), List.of("3", " ", "0"));

		assertThat(cart.rows()).containsExactly(new ShopCart.Row("Apple", "3"), new ShopCart.Row("Bread", "0"));
	}

	@Test
	void isEmpty_whenOnlyBlankOrZeroRows() {
		assertThat(ShopCart.of(List.of("Apple", "Milk", "Bread"), List.of("", "0", "+00")).isEmpty()).isTrue();
		assertThat(ShopCart.of(null, null).isEmpty()).isTrue();
		assertThat(ShopCart.of(List.of("Apple"), List.of("1")).isEmpty()).isFalse();
	}

	@Test
	void toleratesMissingOrNullQuantities() {
		var cart = ShopCart.of(List.of("Apple", "Milk", "Bread"), Arrays.asList("2", null));

		assertThat(cart.rows()).containsExactly(new ShopCart.Row("Apple", "2"));
	}

	@Test
	void parse_leavesOutZeroRows() {
		var cart = ShopCart.of(List.of("Apple", "Milk"), List.of("0", "2"));

		assertThat(cart.parse()).isEqualTo(new Purchase(List.of(new PurchaseItem("Milk", 2))));
	}

	@Test
	void errorsOf_namesTheProduct_evenWhenZeroRowsShiftTheIndexes() {
		var cart = ShopCart.of(List.of("Apple", "Milk", "Bread"), List.of("0", "-2", "abc"));

		var parsed = cart.parse();

		assertThat(parsed).isInstanceOf(ParsedPurchase.Invalid.class);
		assertThat(cart.errorsOf((ParsedPurchase.Invalid) parsed))
			.containsExactly("Milk: must be greater than or equal to 1", "Bread: must be a number");
	}
}
