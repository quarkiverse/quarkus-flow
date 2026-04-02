package io.quarkiverse.flow.persistence.redis;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import io.serverlessworkflow.impl.marshaller.MarshallingUtils;
import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;

final class RedisInstantCodec {

    private RedisInstantCodec() {
    }

    static byte[] encode(Instant instant) {
        return instant == null ? null : instant.toString().getBytes(StandardCharsets.UTF_8);
    }

    static Instant decode(WorkflowBufferFactory factory, byte[] data) {
        if (data == null) {
            return null;
        }

        try {
            return Instant.parse(new String(data, StandardCharsets.UTF_8));
        } catch (RuntimeException ignored) {
            if (factory == null) {
                throw new IllegalStateException("WorkflowBufferFactory is required to decode legacy Redis instants");
            }
            return MarshallingUtils.readInstant(factory, data);
        }
    }
}
