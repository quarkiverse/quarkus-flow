package io.quarkiverse.flow.dsl.serialization.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.serverlessworkflow.api.types.jackson.OutputAsMixIn;

@JsonSerialize(using = FuncExportAsSerializer.class)
@JsonDeserialize(using = FuncExportAsDeserializer.class)
public class FuncExportAsMixIn extends OutputAsMixIn {
}
