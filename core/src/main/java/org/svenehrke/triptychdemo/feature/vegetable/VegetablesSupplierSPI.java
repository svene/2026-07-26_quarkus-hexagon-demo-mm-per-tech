package org.svenehrke.triptychdemo.feature.vegetable;

public interface VegetablesSupplierSPI {
	void placeOrder(String productName, int quantity);
}
