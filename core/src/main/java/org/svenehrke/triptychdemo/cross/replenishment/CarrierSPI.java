package org.svenehrke.triptychdemo.cross.replenishment;

/**
 * The carrier that takes shipments from the DC to the stores and the online FC. It reports each arrival (by shipment
 * id) back asynchronously; a shipment may be dispatched more than once (see
 * {@link ReplenishmentHandler#redispatchOverdue}), and its arrival may be reported more than once.
 */
public interface CarrierSPI {

	void dispatch(Shipment shipment);
}
