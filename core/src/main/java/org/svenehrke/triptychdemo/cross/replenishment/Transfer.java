package org.svenehrke.triptychdemo.cross.replenishment;

/** The DC shipped {@code shipment} for {@code request}, whose state afterwards this is. */
public record Transfer(ReplenishmentRequest request, Shipment shipment) {

	public int quantity() {
		return shipment.quantity();
	}
}
