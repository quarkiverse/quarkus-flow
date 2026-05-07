package io.quarkiverse.flow.recorders;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;
import io.serverlessworkflow.impl.jackson.ObjectMapperFactoryProvider;

@Recorder
public class SDKRecorder {
    /**
     * Replace the static ObjectMapper from the SDK with Quarkus' bean if presented on classpath
     */
    public void injectQuarkusObjectMapper(BeanContainer beanContainer) {
        ObjectMapper mapper = beanContainer.beanInstance(ObjectMapper.class);
        if (mapper != null) {
            ObjectMapperFactoryProvider.instance().setFactory(() -> mapper);
        }
    }
}
