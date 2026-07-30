package com.example.hexarcdemo.core.port.in;

import com.example.hexarcdemo.core.domain.Product;
import java.util.List;

public interface ProductsAPI {
    List<Product> listAll();
}
