package org.svenehrke.triptychdemo.feature.meat;

public interface MeatSupplierSPI {
	void placeOrder(String productName, int quantity);
}
