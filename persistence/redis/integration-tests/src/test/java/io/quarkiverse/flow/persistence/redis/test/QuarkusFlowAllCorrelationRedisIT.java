package io.quarkiverse.flow.persistence.redis.test;

import jakarta.inject.Inject;

import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.persistence.test.AbstractCorrelationPersistenceTest;
import io.serverlessworkflow.impl.scheduler.AllStrategyCorrelationInfoFactory;

@QuarkusTest
@DisabledOnOs(OS.WINDOWS)
public class QuarkusFlowAllCorrelationRedisIT extends AbstractCorrelationPersistenceTest {

    @Inject
    AllStrategyCorrelationInfoFactory factory;

    @Override
    protected AllStrategyCorrelationInfoFactory getAllStrategyCorrelationInfoFactory() {
        return factory;
    }
}
