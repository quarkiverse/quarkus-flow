package org.acme.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Map;

//import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import io.quarkus.test.common.QuarkusTestResource;

//@Disabled("Should not run on CI since it depends on Petstore API reliability. Here just for demonstration.")
@QuarkusTest
@QuarkusTestResource(PetstoreMockResource.class)
class PetstoreFlowIT {

    @Inject
    PetstoreFlow petstoreFlow;

    @Test
    void petstoreWorkflowShouldReturnPetDetails() throws Exception {
        // This will trigger:
        // 1) openapi() call against the Petstore OpenAPI document
        // 2) GET https://petstore.swagger.io/v2/pet/{selectedPetId}
        //
        // and give us the final workflow context as a Map.
        Map<String, Object> pet = petstoreFlow.instance(Map.of()).start().get().asMap().orElseThrow();

        // Very lightweight sanity checks – we mainly care that the flow ran end-to-end
        assertThat(pet).isNotNull();
        assertThat(pet).isNotEmpty();
        assertThat(((Number) pet.get("id")).intValue())
                .as("pet id from mocked response")
                .isEqualTo(123);
        assertThat((String) pet.get("name"))
                .as("pet name from mocked response")
                .isEqualTo("Mocked Pet");

    }
}
