package org.svenehrke.triptychdemo.core.domain;

public record Product(String name, ProductType type, int availableAmount) {}
