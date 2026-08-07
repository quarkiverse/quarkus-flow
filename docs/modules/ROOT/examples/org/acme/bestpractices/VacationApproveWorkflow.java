package org.acme.bestpractices;

import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowDSL.tasks;
import static io.quarkiverse.flow.dsl.FlowDSL.tryCatch;
import static io.quarkiverse.flow.dsl.FlowWorkflowBuilder.workflow;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class VacationApproveWorkflow extends Flow {

    @Inject
    ApprovalService approvalService;

    @Override
    public Workflow descriptor() {
        return workflow("vacation-approval")
                .tasks(
                        tryCatch(
                                "tryApproval",
                                t -> t.tryCatch(tasks(
                                        function("submitRequest", approvalService::submit),
                                        function("checkApproval", approvalService::requireApproval)))
                                        .catchHandler(handler -> handler
                                                .errorsWith(err -> err.type("APPROVAL_REJECTED"))
                                                .retry(r -> r
                                                        .limit(limit -> limit.attempt(a -> a.count(3)))
                                                        .delay("PT1H"))
                                                .doTasks(tasks(
                                                        function("notifyRejection", approvalService::notifyRejection,
                                                                VacationRequest.class))))))
                .build();
    }
}
