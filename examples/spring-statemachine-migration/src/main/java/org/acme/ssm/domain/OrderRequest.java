package org.acme.ssm.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Starts the order lifecycle. Equivalent to the event that drives a Spring
 * StateMachine from its initial state into {@code AWAITING_PAYMENT}.
 */
@RegisterForReflection
public record OrderRequest(String orderId, String customer, double amount) {
}
