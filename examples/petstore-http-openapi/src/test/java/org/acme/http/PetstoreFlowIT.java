package org.acme.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class PetstoreFlowIT {

    @Inject
    PetstoreFlow petstoreFlow;

    @Test
    void petstoreWorkflowShouldReturnPetDetails() throws Exception {
        // This will trigger:
        //  1) openapi() call against the Petstore OpenAPI document
        //  2) GET https://petstore.swagger.io/v2/pet/{selectedPetId}
        //
        // and give us the final workflow context as a Map.
        @SuppressWarnings("unchecked")
        Map<String, Object> pet = petstoreFlow
                .startInstance(Map.of())
                .thenApply(w -> w.as(Map.class).orElseThrow())
                .toCompletableFuture()
                .get(30, TimeUnit.SECONDS); // avoid hanging forever if Petstore is down

        // Very lightweight sanity checks – we mainly care that the flow ran end-to-end
        assertThat(pet).isNotNull();
        assertThat(pet).isNotEmpty();
        assertThat(pet.get("id"))
                .as("pet id from Petstore response")
                .isNotNull();
    }
}
