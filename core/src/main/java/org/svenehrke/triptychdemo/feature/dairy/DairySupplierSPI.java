package org.svenehrke.triptychdemo.feature.dairy;

public interface DairySupplierSPI {
	void placeOrder(String productName, int quantity);
}
