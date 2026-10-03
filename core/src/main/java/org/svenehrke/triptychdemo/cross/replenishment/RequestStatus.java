package org.svenehrke.triptychdemo.cross.replenishment;

public enum RequestStatus {
	/** Not (fully) delivered yet; waits for DC stock or for head office. */
	PENDING,
	/** Everything requested has been delivered. */
	FULFILLED,
	/** Head office cancelled the rest; what was delivered before stays delivered. */
	REJECTED
}
