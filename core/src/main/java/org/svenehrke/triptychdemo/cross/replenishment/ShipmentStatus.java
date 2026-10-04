package org.svenehrke.triptychdemo.cross.replenishment;

public enum ShipmentStatus {
	/** Shipped by the DC, not booked by the location yet. */
	IN_TRANSIT,
	/** Booked into the location's stock. */
	ARRIVED
}
