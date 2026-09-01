package io.quarkiverse.flow.dsl.serialization.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.serverlessworkflow.api.types.jackson.ForInMixIn;

@JsonSerialize(using = FuncForInSerializer.class)
@JsonDeserialize(using = FuncForInDeserializer.class)
public abstract class FuncForInMixIn extends ForInMixIn {
}
