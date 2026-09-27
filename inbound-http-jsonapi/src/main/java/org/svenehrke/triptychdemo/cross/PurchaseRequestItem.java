package org.svenehrke.triptychdemo.cross;

/** One item of a {@link PurchaseRequest}; its structure is checked by {@code PurchaseRequest.structureErrors}. */
public record PurchaseRequestItem(String productName, Integer quantity) {}
