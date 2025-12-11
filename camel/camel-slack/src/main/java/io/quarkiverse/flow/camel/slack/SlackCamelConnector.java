package io.quarkiverse.flow.camel.slack;

import io.quarkiverse.flow.camel.AbstractCamelInvoker;
import io.quarkiverse.flow.camel.CamelConnector;

public class SlackCamelConnector<T, R> extends AbstractCamelInvoker implements CamelConnector<T, R> {

    private final String channel;
    private final String webHookUrlConfigKey;

    public SlackCamelConnector(String channel, String webHookUrlConfigKey) {
        this.channel = channel;
        this.webHookUrlConfigKey = webHookUrlConfigKey;
    }

    @SuppressWarnings("unchecked")
    @Override
    public R apply(T t) {
        return (R) this.invoke(t);
    }

    @Override
    protected String configureEndpoint() {
        return "slack:#" + channel + "?webhookUrl={{" + webHookUrlConfigKey + "}}";
    }

    @Override
    public String connectorName() {
        return "slack";
    }
}
