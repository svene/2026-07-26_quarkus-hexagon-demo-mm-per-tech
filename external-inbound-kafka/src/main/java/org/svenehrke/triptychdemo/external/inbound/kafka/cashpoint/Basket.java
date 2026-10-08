package org.svenehrke.triptychdemo.external.inbound.kafka.cashpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * What one customer buys at a till, from what the store has in stock: a supermarket basket of 4-15 different
 * products, most often about 8, mostly one unit each, now and then two or a multipack of 3-6 - about 9 products and
 * 16 units on average. Never more than the store has in stock.
 */
final class Basket {

    private static final double MIN_PRODUCTS = 4;
    private static final double TYPICAL_PRODUCTS = 8;
    private static final double MAX_PRODUCTS = 15;
    private static final double ONE_UNIT = 0.60;
    private static final double TWO_UNITS = 0.25;
    private static final int MIN_MULTIPACK = 3;
    private static final int MAX_MULTIPACK = 6;

    private Basket() {}

    /** {@code available}: products with stock; empty → an empty basket. */
    static List<PurchaseRequestItem> pick(List<ProductInfo> available, RandomGenerator random) {
        var shuffled = new ArrayList<>(available);
        Collections.shuffle(shuffled, random);
        int count = Math.min(productCount(random), shuffled.size());
        return shuffled.subList(0, count).stream()
            .map(p -> new PurchaseRequestItem(p.name(), Math.min(quantity(random), p.availableAmount())))
            .toList();
    }

    /** Triangular distribution between the min and max, peaking at the typical basket. */
    static int productCount(RandomGenerator random) {
        double u = random.nextDouble();
        double range = MAX_PRODUCTS - MIN_PRODUCTS;
        double split = (TYPICAL_PRODUCTS - MIN_PRODUCTS) / range;
        double count = u < split
            ? MIN_PRODUCTS + Math.sqrt(u * range * (TYPICAL_PRODUCTS - MIN_PRODUCTS))
            : MAX_PRODUCTS - Math.sqrt((1 - u) * range * (MAX_PRODUCTS - TYPICAL_PRODUCTS));
        return (int) Math.round(count);
    }

    static int quantity(RandomGenerator random) {
        double u = random.nextDouble();
        if (u < ONE_UNIT) return 1;
        if (u < ONE_UNIT + TWO_UNITS) return 2;
        return random.nextInt(MIN_MULTIPACK, MAX_MULTIPACK + 1);
    }
}
