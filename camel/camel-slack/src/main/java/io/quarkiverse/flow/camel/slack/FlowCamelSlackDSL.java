package io.quarkiverse.flow.camel.slack;

import io.quarkiverse.flow.camel.CamelConnector;

public final class FlowCamelSlackDSL {

    private FlowCamelSlackDSL() {
    }

    public static <T, R> CamelConnector<T, R> slack(String channel, String webHookUrlConfigKey) {
        return new SlackCamelConnector<>(channel, webHookUrlConfigKey);
    }

}
