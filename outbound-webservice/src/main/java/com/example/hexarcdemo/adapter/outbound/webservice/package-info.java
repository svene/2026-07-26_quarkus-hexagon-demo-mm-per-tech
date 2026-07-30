/**
 * Outbound adapter: SOAP client for the external beverage supplier webservice.
 * Implements an outbound port declared in core via Apache CXF.
 * The SOAP SEI (BeverageOrderService) is intentionally duplicated in the corresponding
 * stub module — the WSDL contract is the wire interface, not a shared Java type.
 */
package com.example.hexarcdemo.adapter.outbound.webservice;
