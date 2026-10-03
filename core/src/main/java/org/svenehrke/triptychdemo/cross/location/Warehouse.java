package org.svenehrke.triptychdemo.cross.location;

/** The central distribution centre: receives every supplier delivery, replenishes the other locations. */
public record Warehouse(String id, String name) implements Location {}
