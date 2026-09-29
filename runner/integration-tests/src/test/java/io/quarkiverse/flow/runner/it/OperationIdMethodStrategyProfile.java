package io.quarkiverse.flow.runner.it;

import java.util.HashMap;
import java.util.Map;

/**
 * Test profile that makes SmallRye OpenAPI derive operation IDs from Java method names.
 * Reuses the NONE security setup, so no identity provider is needed.
 */
public class OperationIdMethodStrategyProfile extends SecurityNoneProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> overrides = new HashMap<>(super.getConfigOverrides());
        overrides.put("quarkus.smallrye-openapi.operation-id-strategy", "METHOD");
        return overrides;
    }

    @Override
    public String getConfigProfile() {
        return "operation-id-method";
    }
}
