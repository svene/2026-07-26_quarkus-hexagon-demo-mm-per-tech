package org.svenehrke.triptychdemo.cross.location;

/** The online fulfilment centre (dark store): sells via the shop and the JSON API. */
public record OnlineFc(String id, String name) implements Replenished {}
