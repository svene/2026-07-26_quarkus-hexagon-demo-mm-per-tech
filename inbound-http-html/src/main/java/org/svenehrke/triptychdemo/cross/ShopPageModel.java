package org.svenehrke.triptychdemo.cross;

import java.util.List;

public record ShopPageModel(List<ProductRowModel> products, List<String> errors) {}
