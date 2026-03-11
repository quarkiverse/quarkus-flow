package io.quarkiverse.flow.it;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.get;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class GetContributorsFlow extends Flow {

    @ConfigProperty(name = "wiremock.url")
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
