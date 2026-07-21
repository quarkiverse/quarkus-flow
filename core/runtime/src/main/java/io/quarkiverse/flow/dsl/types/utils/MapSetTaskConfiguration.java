package io.quarkiverse.flow.dsl.types.utils;

import java.util.Map;

import io.serverlessworkflow.api.types.SetTaskConfiguration;

public class MapSetTaskConfiguration {

    private MapSetTaskConfiguration() {
    }

    public static SetTaskConfiguration map(Map<String, Object> map) {
        SetTaskConfiguration config = new SetTaskConfiguration();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            config.withAdditionalProperty(entry.getKey(), entry.getValue());
        }
        return config;
    }
}
