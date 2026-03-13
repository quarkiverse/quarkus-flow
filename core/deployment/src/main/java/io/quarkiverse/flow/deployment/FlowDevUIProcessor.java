package io.quarkiverse.flow.deployment;

import io.quarkiverse.flow.config.FlowDevUIConfig;
import io.quarkiverse.flow.devui.InMemoryWorkflowInstanceStore;
import io.quarkiverse.flow.devui.InjectManagementListenerRecorder;
import io.quarkiverse.flow.devui.MVStoreWorkflowInstanceStore;
import io.quarkiverse.flow.devui.ManagementLifecycleListener;
import io.quarkiverse.flow.devui.ManagementLifecycleRPCService;
import io.quarkiverse.flow.devui.WorkflowRPCService;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.IsLocalDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

public class FlowDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem card() {
        CardPageBuildItem cardPage = new CardPageBuildItem();
        cardPage.addPage(Page.webComponentPageBuilder()
                .title("Workflows")
                .componentLink("qwc-flow-workflows.js")
                .dynamicLabelJsonRPCMethodName(
                        "getNumbersOfWorkflows")
                .icon("font-awesome-solid:diagram-project"));
        cardPage.addLibraryVersion("io.serverlessworkflow", "serverlessworkflow-impl-core", "CNCF Workflow SDK",
                "https://serverlessworkflow.io/");
        cardPage.addLibraryVersion("io.cloudevents", "cloudevents-json-jackson", "CloudEvents SDK",
                "https://cloudevents.github.io/sdk-java/");
        cardPage.addLibraryVersion("net.thisptr", "jackson-jq", "Jackson JQ", "https://github.com/eiiches/jackson-jq");
        return cardPage;
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    void createJsonRPCProviders(BuildProducer<JsonRPCProvidersBuildItem> rpcProviders) {
        rpcProviders.produce(new JsonRPCProvidersBuildItem(WorkflowRPCService.class));
        rpcProviders.produce(new JsonRPCProvidersBuildItem(ManagementLifecycleRPCService.class));
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    AdditionalBeanBuildItem additionalBeans(FlowDevUIConfig flowDevUIConfig) {

        AdditionalBeanBuildItem.Builder builder = AdditionalBeanBuildItem.builder()
                .addBeanClasses(
                        ManagementLifecycleListener.class);

        switch (flowDevUIConfig.storageType()) {
            case MVSTORE -> builder.addBeanClass(MVStoreWorkflowInstanceStore.class);
            case IN_MEMORY -> builder.addBeanClass(InMemoryWorkflowInstanceStore.class);
        }

        return builder
                .setUnremovable()
                .build();
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    void produceApplicationBuilder(InjectManagementListenerRecorder recorder, WorkflowApplicationBuilderBuildItem builderItem) {
        recorder.addManagementLifecycleListener(builderItem.builder());
    }
}
