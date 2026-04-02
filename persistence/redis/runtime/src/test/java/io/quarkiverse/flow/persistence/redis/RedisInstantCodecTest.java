package io.quarkiverse.flow.persistence.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class RedisInstantCodecTest {

    @Test
    void should_round_trip_instant_as_text() {
        Instant instant = Instant.parse("2026-04-02T04:55:41.959137792Z");

        byte[] encoded = RedisInstantCodec.encode(instant);

        assertEquals(instant, RedisInstantCodec.decode(null, encoded));
    }

    @Test
    void should_handle_null_values() {
        assertNull(RedisInstantCodec.encode(null));
        assertNull(RedisInstantCodec.decode(null, null));
    }
}
