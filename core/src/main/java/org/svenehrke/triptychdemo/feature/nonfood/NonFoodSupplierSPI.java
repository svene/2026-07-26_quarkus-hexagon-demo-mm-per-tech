package org.svenehrke.triptychdemo.feature.nonfood;

public interface NonFoodSupplierSPI {
	void placeOrder(String productName, int quantity);
}
