package org.svenehrke.triptychdemo.cross.products;

import java.util.List;

import static org.svenehrke.triptychdemo.cross.products.ProductType.*;

/**
 * The products the chain lists: what the admin order forms offer and what the DC is seeded with. Only a list, not a
 * rule - orders of other names stay allowed (the tests order unique product names through the JSON API).
 */
public final class Catalog {

    public static final List<CatalogProduct> PRODUCTS = List.of(
        new CatalogProduct("Mango", FRUIT), new CatalogProduct("Banana", FRUIT),
        new CatalogProduct("Apple", FRUIT), new CatalogProduct("Orange", FRUIT),
        new CatalogProduct("Carrot", VEGETABLE), new CatalogProduct("Potato", VEGETABLE),
        new CatalogProduct("Tomato", VEGETABLE), new CatalogProduct("Cucumber", VEGETABLE),
        new CatalogProduct("Milk", DAIRY), new CatalogProduct("Cheese", DAIRY),
        new CatalogProduct("Yogurt", DAIRY), new CatalogProduct("Butter", DAIRY),
        new CatalogProduct("Cola", BEVERAGE), new CatalogProduct("Water", BEVERAGE),
        new CatalogProduct("Juice", BEVERAGE), new CatalogProduct("Beer", BEVERAGE),
        new CatalogProduct("Chicken", MEAT), new CatalogProduct("Beef", MEAT),
        new CatalogProduct("Pork", MEAT), new CatalogProduct("Lamb", MEAT),
        new CatalogProduct("Bread", BAKERY), new CatalogProduct("Croissant", BAKERY),
        new CatalogProduct("Baguette", BAKERY), new CatalogProduct("Pretzel", BAKERY),
        new CatalogProduct("Detergent", NON_FOOD), new CatalogProduct("Soap", NON_FOOD),
        new CatalogProduct("Sponge", NON_FOOD), new CatalogProduct("Paper towels", NON_FOOD));

    private Catalog() {}
}
