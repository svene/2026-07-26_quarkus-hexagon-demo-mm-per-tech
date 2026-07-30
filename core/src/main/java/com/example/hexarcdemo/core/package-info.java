/**
 * Domain + application core.
 *
 * Contains entities/value objects, use cases (application services), and all
 * inbound and outbound ports (interfaces). This module must never depend on
 * any adapter module or any infrastructure library (no JDBC, no Kafka client,
 * no HTTP client, no Mongo driver).
 *
 * Suggested sub-packages as you flesh this out:
 *   .domain              - entities, value objects, domain services
 *   .application          - use case implementations
 *   .port.in               - inbound ports (interfaces the adapters call)
 *   .port.out             - outbound ports (interfaces the adapters implement)
 */
package com.example.hexarcdemo.core;
