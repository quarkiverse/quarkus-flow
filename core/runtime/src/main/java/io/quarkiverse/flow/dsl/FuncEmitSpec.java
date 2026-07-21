package io.quarkiverse.flow.dsl;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import io.cloudevents.CloudEventData;
import io.cloudevents.core.data.BytesCloudEventData;
import io.cloudevents.core.data.PojoCloudEventData;
import io.quarkiverse.flow.dsl.configurers.FuncEmitConfigurer;
import io.quarkiverse.flow.dsl.types.ContextFunction;
import io.quarkiverse.flow.dsl.types.EventDataFunction;
import io.quarkiverse.flow.dsl.types.SerializableFunction;
import io.quarkiverse.flow.dsl.types.utils.ReflectionUtils;
import io.serverlessworkflow.fluent.spec.dsl.EventEmitPropertiesSpec;
import io.serverlessworkflow.impl.jackson.JsonUtils;

public final class FuncEmitSpec
        extends EventEmitPropertiesSpec<FuncEmitSpec, FuncEmitEventPropertiesBuilder>
        implements FuncEmitConfigurer {

    @Override
    protected FuncEmitSpec self() {
        return this;
    }

    /** Sets the event data and the contentType to `application/json` */
    public <T> FuncEmitSpec jsonData(SerializableFunction<T, CloudEventData> function) {
        Class<T> clazz = ReflectionUtils.inferInputType(function);
        addPropertyStep(e -> e.data(new EventDataFunction().withFunction(function, clazz)));
        return JSON();
    }

    /** Sets the event data and the contentType to `application/octet-stream` */
    public <T> FuncEmitSpec bytesData(Function<T, byte[]> serializer, Class<T> clazz) {
        addPropertyStep(
                e -> e.data(payload -> BytesCloudEventData.wrap(serializer.apply(payload)), clazz));
        return OCTET_STREAM();
    }

    public FuncEmitSpec bytesDataUtf8() {
        return bytesData((String s) -> s.getBytes(StandardCharsets.UTF_8), String.class);
    }

    /** Sets the event data and the contentType to `application/json` */
    public <T> FuncEmitSpec jsonData(Function<T, CloudEventData> function, Class<T> clazz) {
        addPropertyStep(e -> e.data(new EventDataFunction().withFunction(function, clazz)));
        return JSON();
    }

    /** JSON with default mapper (PojoCloudEventData + application/json). */
    public <T> FuncEmitSpec jsonData(Class<T> clazz) {
        addPropertyStep(
                e -> e.data(
                        payload -> PojoCloudEventData.wrap(
                                payload, p -> JsonUtils.mapper().writeValueAsString(p).getBytes()),
                        clazz));
        return JSON();
    }

    public <T> FuncEmitSpec jsonData(ContextFunction<T, CloudEventData> function, Class<T> clazz) {
        addPropertyStep(
                e -> e.data(
                        payload -> PojoCloudEventData.wrap(
                                payload, p -> JsonUtils.mapper().writeValueAsString(p).getBytes()),
                        clazz));
        return JSON();
    }

    @Override
    public void accept(FuncEmitTaskBuilder funcEmitTaskBuilder) {
        funcEmitTaskBuilder.event(e -> getPropertySteps().forEach(step -> step.accept(e)));
    }
}
