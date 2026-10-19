package io.quarkiverse.flow.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = FlowDevUIConfig.PREFIX)
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface FlowDevUIConfig {

    String PREFIX = FlowRuntimeConfig.FLOW_ROOT_PREFIX + ".devui";

    FlowDevUIBackendConfig backend();

}
