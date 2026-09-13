package org.svenehrke.triptychdemo.cross.purchase;

import java.util.List;

public interface PurchaseAPI {
	void purchase(List<PurchaseItem> items);
}
