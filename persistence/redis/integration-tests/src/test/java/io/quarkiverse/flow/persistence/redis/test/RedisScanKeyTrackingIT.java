package io.quarkiverse.flow.persistence.redis.test;

import java.util.Map;

import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(RedisScanKeyTrackingIT.ScanKeyTracking.class)
@DisabledOnOs(OS.WINDOWS)
public class RedisScanKeyTrackingIT extends AbstractRedisKeyTrackingIT {

    @Override
    protected boolean indexed() {
        return false;
    }

    public static class ScanKeyTracking implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.flow.persistence.redis.key-tracking", "scan");
        }
    }
}
