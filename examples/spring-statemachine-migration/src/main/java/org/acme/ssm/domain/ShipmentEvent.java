package org.acme.ssm.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * External event delivered while the workflow waits in {@code AWAITING_SHIPMENT}.
 */
@RegisterForReflection
public record ShipmentEvent(String orderId, String carrier, String trackingId) {
}
