package io.quarkiverse.flow.camel;

import org.apache.camel.ProducerTemplate;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;

public abstract class AbstractCamelInvoker {

    private ProducerTemplate producerTemplate;

    protected ProducerTemplate getProducerTemplate() {
        if (producerTemplate != null) {
            return producerTemplate;
        }
        InstanceHandle<ProducerTemplate> handle = Arc.container().instance(ProducerTemplate.class);
        if (handle.isAvailable()) {
            producerTemplate = handle.get();
            return producerTemplate;
        }
        throw new IllegalStateException("Camel ProducerTemplate is not available");
    }

    protected abstract String configureEndpoint();

    public Object invoke(Object body) {
        // TODO: Add FlowHeaders
        return getProducerTemplate().requestBody(configureEndpoint(), body);
    }

}
