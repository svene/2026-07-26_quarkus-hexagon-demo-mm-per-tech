package org.svenehrke.triptychdemo.cross;

/**
 * A product at a store / the online FC, next to what the DC has of it (what a request could get right away), with
 * its learned demand per period and levels - null until the location has a row for the product.
 */
public record LocationProductRowVM(String name, String type, int availableAmount, int dcAvailableAmount,
                                   Double avgDemand, LevelsVM levels) {}
