package io.quarkiverse.flow.deployment;

import org.jboss.jandex.DotName;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.runtime.LaunchMode;

public class FlowTestingProcessor {

    private static final DotName TEST_WORKFLOW_EXECUTION_LISTENER = DotName
            .createSimple("io.quarkiverse.flow.testing.TestWorkflowExecutionListener");
    private static final DotName FLOW_TESTING_PRODUCER = DotName
            .createSimple("io.quarkiverse.flow.testing.FlowTestingProducer");
    private static final DotName WORKFLOW_EVENT_STORE = DotName.createSimple("io.quarkiverse.flow.testing.WorkflowEventStore");

    @BuildStep
    AdditionalBeanBuildItem additionalTestBeans(LaunchModeBuildItem launchMode, CombinedIndexBuildItem index) {
        if (launchMode.getLaunchMode() == LaunchMode.TEST
                && index.getIndex().getClassByName(WORKFLOW_EVENT_STORE) != null) {
            return AdditionalBeanBuildItem.builder()
                    .addBeanClasses(
                            FLOW_TESTING_PRODUCER.toString())
                    .build();
        }
        return null;
    }
}
