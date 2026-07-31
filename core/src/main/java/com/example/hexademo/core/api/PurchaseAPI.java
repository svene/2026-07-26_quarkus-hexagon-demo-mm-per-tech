package com.example.hexademo.core.api;

import com.example.hexademo.core.domain.PurchaseItem;
import java.util.List;

public interface PurchaseAPI {
    void purchase(List<PurchaseItem> items);
}
