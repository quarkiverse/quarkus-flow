package io.quarkiverse.flow.persistence.common.hashing;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.persistence.common.FlowPersistenceConfig;
import io.quarkiverse.flow.persistence.common.hashing.HashingPersistenceConfig.MD5;
import io.quarkiverse.flow.persistence.common.hashing.HashingPersistenceConfig.SDK;
import io.quarkus.arc.DefaultBean;
import io.serverlessworkflow.impl.persistence.hashing.*;

@ApplicationScoped
@DefaultBean
public class QuarkusHashFactory extends DefaultHashFactory {

    @Inject
    FlowPersistenceConfig config;

    @Override
    protected boolean intCondition(byte[] data) {
        MD5 md5Config = config.hashing().md5();
        return md5Config.enabled() && data.length > md5Config.threshold().orElse(MD5HashItem.SIZE_THRESHOLD);
    }

    @Override
    protected boolean md5Condition(byte[] data) {
        SDK sdkConfig = config.hashing().sdk();
        return sdkConfig.enabled() && data.length > sdkConfig.threshold().orElse(IntegerHashItem.SIZE_THRESHOLD);
    }
}
