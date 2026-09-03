package io.quarkiverse.flow.deployment;

import org.jboss.jandex.DotName;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.IsTest;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;

public class FlowTestingProcessor {

    private static final DotName FLOW_TESTING_PRODUCER = DotName
            .createSimple("io.quarkiverse.flow.testing.FlowTestingProducer");

    @BuildStep(onlyIf = IsTest.class)
    void additionalTestBeans(BuildProducer<AdditionalBeanBuildItem> bean, CombinedIndexBuildItem jandex) {
        if (jandex.getIndex().getClassByName(FLOW_TESTING_PRODUCER) != null) {
            bean.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClasses(FLOW_TESTING_PRODUCER.toString())
                    .build());
        }
    }
}
