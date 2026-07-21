package io.quarkiverse.flow.runner.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Authentication mechanism registration tests")
class AuthenticationMechanismRegistrationTest {

    private static final int RUNNER_MECHANISM_PRIORITY = 1000;

    @Test
    @DisplayName("API key mechanism must not replace built-in mechanisms")
    void apiKeyMechanismMustNotBeAlternative() {
        assertIsNonAlternativeRunnerMechanism(
                ApiKeyAuthenticationMechanism.class);
    }

    @Test
    @DisplayName("Permit-all mechanism must not replace built-in mechanisms")
    void permitAllMechanismMustNotBeAlternative() {
        assertIsNonAlternativeRunnerMechanism(
                PermitAllAuthenticationMechanism.class);
    }

    private void assertIsNonAlternativeRunnerMechanism(Class<?> mechanismClass) {
        assertThat(
                mechanismClass.isAnnotationPresent(Alternative.class))
                .as("%s must not be a CDI alternative",
                        mechanismClass.getSimpleName())
                .isFalse();

        Priority priority = mechanismClass.getAnnotation(Priority.class);

        assertThat(priority)
                .as("%s must declare an authentication priority",
                        mechanismClass.getSimpleName())
                .isNotNull();

        assertThat(priority.value())
                .as("%s priority", mechanismClass.getSimpleName())
                .isEqualTo(RUNNER_MECHANISM_PRIORITY);
    }
}
