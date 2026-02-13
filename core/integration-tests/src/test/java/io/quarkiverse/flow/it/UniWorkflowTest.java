package io.quarkiverse.flow.it;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class UniWorkflowTest {

    @Inject
    UniWorkflow def;

    @Test
    void testUni() {
        assertThat(def.startInstance().await().indefinitely().as(String.class).orElseThrow()).isEqualTo("Javierito");
    }
}
