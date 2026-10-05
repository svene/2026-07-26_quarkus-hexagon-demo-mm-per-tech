package org.svenehrke.triptychdemo.cross.reset;

/** Back to an empty demo: deletes the inventory data, but keeps the id sequences, so ids stay unique. */
public interface ResetRepositorySPI {
	/** Deletes the stock of every location, every replenishment request, shipment and supplier order, in one transaction. */
	void deleteAll();
}
