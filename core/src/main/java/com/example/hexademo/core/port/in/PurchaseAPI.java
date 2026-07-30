package com.example.hexademo.core.port.in;

import com.example.hexademo.core.domain.PurchaseItem;
import java.util.List;

public interface PurchaseAPI {
    void purchase(List<PurchaseItem> items);
}
