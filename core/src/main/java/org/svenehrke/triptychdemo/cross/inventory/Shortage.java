package org.svenehrke.triptychdemo.cross.inventory;

/** Less stock of {@code productName} than requested; an unknown product has {@code available} 0. */
public record Shortage(String productName, int requested, int available) {

	/** For a rejected online purchase, e.g. {@code "Apple: only 3 in stock (requested 5)"}. */
	public String message() {
		return available == 0
			? productName + ": not in stock"
			: productName + ": only " + available + " in stock (requested " + requested + ")";
	}

	/** For a physical-store sale that exceeded the recorded stock, e.g. {@code "Apple: sold 5, only 3 on record"}. */
	public String discrepancyMessage() {
		return productName + ": sold " + requested + ", only " + available + " on record";
	}
}
