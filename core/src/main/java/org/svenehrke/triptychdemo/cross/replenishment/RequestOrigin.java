package org.svenehrke.triptychdemo.cross.replenishment;

public enum RequestOrigin {
	/** Someone asked for it on the location's page. */
	MANUAL,
	/** The location's stock fell below its learned reorder point ({@code LearnedLevels}). */
	AUTOMATIC
}
