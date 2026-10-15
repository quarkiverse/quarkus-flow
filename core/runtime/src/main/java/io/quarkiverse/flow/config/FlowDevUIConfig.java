package io.quarkiverse.flow.config;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface FlowDevUIConfig {

    FlowDevUIBackendConfig backend();

}
