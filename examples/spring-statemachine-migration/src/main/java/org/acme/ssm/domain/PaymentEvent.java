package org.acme.ssm.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * External event delivered while the workflow waits in {@code AWAITING_PAYMENT}.
 * Mirrors a Spring StateMachine event that carries a guard-relevant payload:
 * {@code approved} is read by the guard/choice step.
 */
@RegisterForReflection
public record PaymentEvent(String orderId, boolean approved, String reference) {
}
