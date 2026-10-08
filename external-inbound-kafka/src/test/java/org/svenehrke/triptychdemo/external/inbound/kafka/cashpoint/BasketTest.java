package org.svenehrke.triptychdemo.external.inbound.kafka.cashpoint;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BasketTest {

    private static final int CUSTOMERS = 10_000;

    /** Plenty of everything: only the basket itself limits the purchase. */
    private static final List<ProductInfo> WELL_STOCKED = IntStream.range(0, 28)
        .mapToObj(i -> new ProductInfo("p" + i, "FRUIT", 1000))
        .toList();

    @Test
    void a_basket_holds_4_to_15_different_products_about_9_on_average() {
        var random = new SplittableRandom(42);
        var sizes = IntStream.range(0, CUSTOMERS).map(i -> Basket.pick(WELL_STOCKED, random).size()).toArray();

        assertThat(IntStream.of(sizes).min().orElseThrow()).isEqualTo(4);
        assertThat(IntStream.of(sizes).max().orElseThrow()).isEqualTo(15);
        assertThat(IntStream.of(sizes).average().orElseThrow()).isCloseTo(9, within(0.3));
    }

    @Test
    void mostly_one_unit_per_product_up_to_a_multipack_of_6_about_15_units_per_basket() {
        var random = new SplittableRandom(42);
        var baskets = IntStream.range(0, CUSTOMERS).mapToObj(i -> Basket.pick(WELL_STOCKED, random)).toList();
        var quantities = baskets.stream().flatMap(List::stream).mapToInt(PurchaseRequestItem::quantity).toArray();

        assertThat(IntStream.of(quantities).min().orElseThrow()).isEqualTo(1);
        assertThat(IntStream.of(quantities).max().orElseThrow()).isEqualTo(6);
        assertThat(IntStream.of(quantities).filter(q -> q == 1).count() / (double) quantities.length)
            .isCloseTo(0.6, within(0.02));
        assertThat(IntStream.of(quantities).sum() / (double) CUSTOMERS).isCloseTo(16, within(1.0));
    }

    @Test
    void never_more_than_in_stock() {
        var scarce = List.of(new ProductInfo("Apple", "FRUIT", 1), new ProductInfo("Milk", "DAIRY", 2));
        var random = new SplittableRandom(42);

        for (int i = 0; i < 1000; i++) {
            var basket = Basket.pick(scarce, random);
            assertThat(basket).hasSize(2);
            assertThat(basket).allSatisfy(item -> assertThat(item.quantity())
                .isBetween(1, item.productName().equals("Apple") ? 1 : 2));
        }
    }

    @Test
    void nothing_in_stock_an_empty_basket() {
        assertThat(Basket.pick(List.of(), new SplittableRandom(42))).isEmpty();
    }
}
