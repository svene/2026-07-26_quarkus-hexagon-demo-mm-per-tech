package org.svenehrke.triptychdemo.cross.inventory;

/**
 * A demand period was closed: the reorder levels of every location were recalculated, and every DC
 * product has a stock row at each of them.
 */
public record LevelsRecalculated() implements InventoryEvent {}
