package io.quarkiverse.flow.persistence.redis.test;

import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@DisabledOnOs(OS.WINDOWS)
public class RedisIndexedKeyTrackingIT extends AbstractRedisKeyTrackingIT {

    @Override
    protected boolean indexed() {
        return true;
    }
}
