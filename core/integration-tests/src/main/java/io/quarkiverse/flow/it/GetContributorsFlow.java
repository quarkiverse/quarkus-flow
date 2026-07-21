package io.quarkiverse.flow.it;

import static io.quarkiverse.flow.dsl.FlowDSL.get;
import static io.quarkiverse.flow.dsl.FlowWorkflowBuilder.workflow;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class GetContributorsFlow extends Flow {

    @ConfigProperty(name = "named.http.metadata.propagation.url")
    String baseUrl;

    @Override
    public Workflow descriptor() {
        return workflow("sdk-java-repository")
                .tasks(get("getSdkJavaContributors",
                        baseUrl + "/serverlessworkflow/sdk-java/contributors"),
                        get("getQuarkusContributors",
                                baseUrl + "/quarkusio/quarkus/contributors"))
                .build();
    }
}
