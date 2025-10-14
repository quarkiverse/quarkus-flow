package org.acme.newsletter;

import org.acme.newsletter.agents.CriticAgent;
import org.acme.newsletter.agents.DrafterAgent;
import org.acme.newsletter.domain.CriticOutput;
import org.acme.newsletter.services.MailService;

import io.cloudevents.core.data.PojoCloudEventData;
import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.impl.jackson.JsonUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.emit;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.event;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.eventJson;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.fn;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.listen;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.switchWhenOrElse;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.to;


@ApplicationScoped
public class NewsletterWorkflow extends Flow {

    @Inject
    DrafterAgent drafterAgent;

    @Inject
    CriticAgent criticAgent;

    @Inject
    MailService mailService;

    @Inject
    HumanReviewHelper helper;

    @Override
    public Workflow descriptor() {
        // TODO: transform the input into a JSON to keep the UI ergonomic

        return FuncWorkflowBuilder.workflow()
                // TODO: agent(drafterAgent::draft)
                // TODO: in agent we receive a BiFunction(String, T). The first is the memoryId. We then use a JavaFilterFunction to inject the workflowId
                .tasks(f -> f.callFn("draftAgent", fn((String input) -> drafterAgent.draft("ABC", input), String.class)),
                        f -> f.callFn("criticAgent", fn((String input) -> criticAgent.critique("ABC", input), String.class)),
                        emit("draftReady", eventJson("org.acme.email.review.required", CriticOutput.class)),
                        listen("waitHumanReview", to().one(event("org.acme.newsletter.review.done")))
                                .outputAs(helper::unwrapEventArray, Object.class),
                        switchWhenOrElse(helper::needsAnotherRevision, "draftAgent", "sendNewsletter"),
                        function("sendNewsletter", (String reviewedDraft) -> {
                            String draft = helper.extractDraft(reviewedDraft);
                            mailService.send("subscribers@acme.finance.org", "Weekly Newsletter", draft);
                            return null;
                        }, String.class))
                .build();
    }


}
