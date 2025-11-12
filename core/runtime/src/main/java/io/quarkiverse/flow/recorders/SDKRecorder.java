package io.quarkiverse.flow.recorders;

import java.lang.reflect.Field;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.runtime.annotations.Recorder;
import io.serverlessworkflow.impl.jackson.JsonUtils;

@Recorder
public class SDKRecorder {
    /**
     * Replace the static ObjectMapper from the SDK with Quarkus' bean if presented on classpath
     */
    public void injectQuarkusObjectMapper() {
        InjectableInstance<ObjectMapper> mapper = Arc.container().select(ObjectMapper.class);
        if (mapper.isResolvable()) {
            try {
                final Class<?> ju = Class.forName(JsonUtils.class.getName());
                final Field fi = ju.getDeclaredField("mapper");
                fi.setAccessible(true);
                fi.set(null, mapper.get());
            } catch (Exception e) {
                throw new RuntimeException("Failed to replace JsonUtils ObjectMapper's", e);
            }
        }
    }
}
