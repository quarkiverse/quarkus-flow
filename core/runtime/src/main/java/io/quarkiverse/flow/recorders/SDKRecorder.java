package io.quarkiverse.flow.recorders;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.runtime.annotations.Recorder;
import io.serverlessworkflow.impl.jackson.ObjectMapperFactoryProvider;

@Recorder
public class SDKRecorder {
    /**
     * Replace the static ObjectMapper from the SDK with Quarkus' bean if presented on classpath
     */
    public void injectQuarkusObjectMapper() {
        InjectableInstance<ObjectMapper> mapper = Arc.container().select(ObjectMapper.class);
        if (mapper.isResolvable()) {
            ObjectMapperFactoryProvider.instance().setFactory(mapper::get);
        }
    }
}
