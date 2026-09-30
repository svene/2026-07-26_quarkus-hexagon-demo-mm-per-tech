package org.svenehrke.triptychdemo.cross;

import java.util.List;

public record ShopPageVM(List<ProductRowVM> products, List<String> errors) {}
