package io.quarkiverse.flow.dsl.serialization.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.serverlessworkflow.api.types.jackson.OutputAsMixIn;

@JsonSerialize(using = FuncInputFromSerializer.class)
@JsonDeserialize(using = FuncInputFromDeserializer.class)
public class FuncInputFromMixIn extends OutputAsMixIn {
}
