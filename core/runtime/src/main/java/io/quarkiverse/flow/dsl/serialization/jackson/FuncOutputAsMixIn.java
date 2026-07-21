package io.quarkiverse.flow.dsl.serialization.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.serverlessworkflow.api.types.jackson.OutputAsMixIn;

@JsonSerialize(using = FuncOutputAsSerializer.class)
@JsonDeserialize(using = FuncOutputAsDeserializer.class)
public class FuncOutputAsMixIn extends OutputAsMixIn {
}
