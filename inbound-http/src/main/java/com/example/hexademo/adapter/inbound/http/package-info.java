/**
 * Inbound HTTP adapter: JAX-RS resources for both HTML UI and JSON API.
 * Subpackages:
 *   - html: HTML user interfaces (AdminReceiver, ShopReceiver)
 *   - jsonapi: JSON API endpoints (ProductApiReceiver)
 * Shared: request/response classes (fruit.OrderRequest, etc.) for both HTML and JSON API.
 * Must not contain business logic - only request/response mapping and validation.
 */
package com.example.hexademo.adapter.inbound.http;
