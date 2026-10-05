package org.acme.ssm.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Terminal output of the workflow. {@code finalState} records which end state
 * the machine reached: {@code DELIVERED} or {@code CANCELLED}.
 */
@RegisterForReflection
public record OrderResult(String orderId, String finalState, String detail) {
}
