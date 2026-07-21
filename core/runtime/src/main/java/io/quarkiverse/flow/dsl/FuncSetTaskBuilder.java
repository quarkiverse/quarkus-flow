package io.quarkiverse.flow.dsl;

import java.util.Map;

import io.quarkiverse.flow.dsl.spi.ConditionalTaskBuilder;
import io.quarkiverse.flow.dsl.types.utils.MapSetTaskConfiguration;
import io.serverlessworkflow.fluent.spec.SetTaskBuilder;

public class FuncSetTaskBuilder extends SetTaskBuilder
        implements ConditionalTaskBuilder<FuncSetTaskBuilder> {

    public FuncSetTaskBuilder expr(Map<String, Object> map) {
        this.setTaskConfiguration = MapSetTaskConfiguration.map(map);
        return this;
    }
}
