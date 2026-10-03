package org.svenehrke.triptychdemo.cross.location;

/** A location that sells, and is replenished from the DC (the {@link Warehouse}) on request. */
public sealed interface Replenished extends Location permits Store, OnlineFc {}
