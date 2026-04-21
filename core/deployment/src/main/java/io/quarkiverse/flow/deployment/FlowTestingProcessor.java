package io.quarkiverse.flow.deployment;

import io.quarkiverse.flow.testing.TestWorkflowExecutionListener;
import io.quarkiverse.flow.testing.WorkflowEventRecorder;
import io.quarkiverse.flow.testing.WorkflowEventStore;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.runtime.LaunchMode;

public class FlowTestingProcessor {

    @BuildStep
    AdditionalBeanBuildItem additionalBeans(LaunchModeBuildItem launchMode) {
        if (launchMode.getLaunchMode() == LaunchMode.TEST) {
            return new AdditionalBeanBuildItem(TestWorkflowExecutionListener.class, WorkflowEventRecorder.class,
                    WorkflowEventStore.class);
        }
        return null;
    }
}
