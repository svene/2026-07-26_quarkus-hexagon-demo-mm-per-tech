package org.svenehrke.triptychdemo.cross;

/**
 * A product at a store / the online FC: what it has, what is on its way to it from the DC, and what the DC has of it
 * (what a request could get right away), with its learned demand per period and levels - null until the location has
 * a row for the product.
 */
public record LocationProductRowVM(String name, String type, int availableAmount, int inTransit, int dcAvailableAmount,
                                   Double avgDemand, LevelsVM levels) {}
