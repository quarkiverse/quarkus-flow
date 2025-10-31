package io.quarkiverse.flow.deployment;

import io.quarkiverse.flow.devui.WorkflowRPCService;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.IsLocalDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
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
        return cardPage;
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    JsonRPCProvidersBuildItem createJsonRPCProviders() {
        return new JsonRPCProvidersBuildItem(WorkflowRPCService.class);
    }
}
