package io.quarkiverse.flow.oidc.deployment;

import io.quarkiverse.flow.oidc.FlowOidcAuthCustomizer;
import io.quarkiverse.flow.oidc.OidcClientFactory;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class FlowOidcProcessor {

    private static final String FEATURE = "flow-oidc";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> beans) {
        beans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(FlowOidcAuthCustomizer.class, OidcClientFactory.class)
                .setUnremovable()
                .build());
    }
}
