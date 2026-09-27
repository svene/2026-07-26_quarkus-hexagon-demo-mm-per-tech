package org.svenehrke.triptychdemo.cross;

/**
 * Shared messages for the JSON API request records' {@code structureErrors(request)}.
 * <p>
 * The request records are deliberately unvalidated: validation constraints live once on the domain
 * type each request is turned into (e.g. {@code FruitOrder}), not duplicated onto these wire-format
 * DTOs. {@code structureErrors(request)} only checks the request's <em>structure</em> (a missing body,
 * field or list entry) and reports every such error, not just the first, which the domain {@code parse()} can't see. The values are validated
 * afterwards by the domain {@code parse()}, called from the receiver. It is static because a missing
 * body arrives as {@code request == null}. {@code quantity} is an {@code Integer}, not an {@code int},
 * so a missing or {@code null} value stays {@code null} instead of silently becoming {@code 0}.
 */
final class RequestStructureErrorMessages {

    static final String BODY_REQUIRED = "request body is required";
    static final String PRODUCT_NAME_REQUIRED = "productName is required";
    static final String QUANTITY_REQUIRED = "quantity is required";

    private RequestStructureErrorMessages() {}
}
