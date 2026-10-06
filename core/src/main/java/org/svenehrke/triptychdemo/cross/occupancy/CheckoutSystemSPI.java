package org.svenehrke.triptychdemo.cross.occupancy;

/** The stores' checkout systems (external): they own the tills. */
public interface CheckoutSystemSPI {

	/** Throws if the checkout system refuses the change or cannot be reached. */
	void setTills(TillCount tillCount);
}
