package org.svenehrke.triptychdemo.feature.bakery;

public interface BakerySupplierSPI {
	void placeOrder(String productName, int quantity);
}
