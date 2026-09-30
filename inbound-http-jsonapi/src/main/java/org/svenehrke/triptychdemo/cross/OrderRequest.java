package org.svenehrke.triptychdemo.cross;

/** Common shape of the {@code POST /api/products/order-...} JSON bodies, for logging the raw request. */
interface OrderRequest {
    String productName();
    Integer quantity();
}
