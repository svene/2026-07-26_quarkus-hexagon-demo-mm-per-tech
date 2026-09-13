package org.svenehrke.triptychdemo.feature.beverage;

public interface BeverageSupplierSPI {
	void placeOrder(String productName, int quantity);
}
