package io.quarkiverse.flow.persistence.common.hashing;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.persistence.common.FlowPersistenceConfig;
import io.quarkus.arc.DefaultBean;
import io.serverlessworkflow.impl.persistence.hashing.*;

@ApplicationScoped
@DefaultBean
public class QuarkusHashFactory extends DefaultHashFactory {

    @Inject
    FlowPersistenceConfig config;

    @Override
    protected boolean intCondition(byte[] data) {
        return data.length > config.hashing().md5().threshold().orElse(MD5HashItem.SIZE_THRESHOLD);
    }

    @Override
    protected boolean md5Condition(byte[] data) {
        return data.length > config.hashing().md5().threshold().orElse(IntegerHashItem.SIZE_THRESHOLD);
    }
}
