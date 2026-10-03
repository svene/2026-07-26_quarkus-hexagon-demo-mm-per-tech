package org.svenehrke.triptychdemo.cross;

/** A product at a store / the online FC, next to what the DC has of it (what a request could get right away). */
public record LocationProductRowVM(String name, String type, int availableAmount, int dcAvailableAmount) {}
