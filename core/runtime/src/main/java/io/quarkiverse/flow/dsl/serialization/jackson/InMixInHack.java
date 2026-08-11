package io.quarkiverse.flow.dsl.serialization.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.serverlessworkflow.api.types.jackson.InSerializer;

@JsonSerialize(using = InSerializer.class)
@JsonDeserialize(using = InDeserializerHack.class)
/*
 * This class should be removed when https://github.com/open-workflow-specification/sdk-java/issues/1613 is fixed
 */
public abstract class InMixInHack {

}
