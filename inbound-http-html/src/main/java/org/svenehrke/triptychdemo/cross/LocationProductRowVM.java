package org.svenehrke.triptychdemo.cross;

/**
 * A product at a store / the online FC: what it has and what is on its way to it from the DC, with its learned demand
 * per period and levels - null until the location has a row for the product.
 */
public record LocationProductRowVM(String name, String type, int availableAmount, int inTransit, Double avgDemand,
                                   LevelsVM levels) {}
