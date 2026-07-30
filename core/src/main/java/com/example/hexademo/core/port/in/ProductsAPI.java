package com.example.hexademo.core.port.in;

import com.example.hexademo.core.domain.Product;
import java.util.List;

public interface ProductsAPI {
    List<Product> listAll();
}
