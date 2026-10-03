package org.svenehrke.triptychdemo.cross.location;

/** A physical store: sells at its cashpoints. */
public record Store(String id, String name) implements Replenished {}
