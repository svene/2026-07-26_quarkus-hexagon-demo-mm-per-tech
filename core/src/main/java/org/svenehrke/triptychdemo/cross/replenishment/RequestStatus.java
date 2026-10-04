package org.svenehrke.triptychdemo.cross.replenishment;

public enum RequestStatus {
	/** Not (fully) shipped yet; waits for DC stock or for head office. */
	PENDING,
	/** Everything requested has been shipped (it may still be in transit). */
	FULFILLED,
	/** Head office cancelled the rest; what was shipped before stays shipped. */
	REJECTED
}
