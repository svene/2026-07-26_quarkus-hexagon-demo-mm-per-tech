package com.example.hexdemo.core.port.in;

import com.example.hexdemo.core.domain.Product;
import java.util.List;

public interface ProductsAPI {
    List<Product> listAll();
}
