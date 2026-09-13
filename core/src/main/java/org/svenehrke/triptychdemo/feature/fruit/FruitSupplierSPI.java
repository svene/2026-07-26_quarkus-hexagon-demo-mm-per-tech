package org.svenehrke.triptychdemo.feature.fruit;

public interface FruitSupplierSPI {
	void placeOrder(String productName, int quantity);
}
