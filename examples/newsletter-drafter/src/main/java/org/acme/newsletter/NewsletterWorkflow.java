package org.acme.newsletter;

import org.acme.newsletter.agents.CriticAgent;
import org.acme.newsletter.agents.DrafterAgent;
import org.acme.newsletter.domain.CriticOutput;

import io.cloudevents.core.data.PojoCloudEventData;
import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.impl.jackson.JsonUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.event;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.fn;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.switchWhenOrElse;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.to;


@ApplicationScoped
public class NewsletterWorkflow extends Flow {

    @Inject
    DrafterAgent drafterAgent;

    @Inject
    CriticAgent criticAgent;

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
                        e -> e.emit("draftReady", event("org.acme.email.review.required",
                                // TODO: incorporate into the DSL
                                payload -> PojoCloudEventData.wrap(payload, p -> JsonUtils.mapper().writeValueAsString(payload).getBytes()), CriticOutput.class)),
                        l -> l.listen("waitHumanReview", to().one(event("org.acme.newsletter.review.done"))
                                // TODO: incorporate this into DSL
                                .andThen(o -> o.outputAs(helper::unwrapToString, Object.class))),
                        switchWhenOrElse(helper::needsAnotherRevision, "draftAgent", "sendNewsletter"),
                        // TODO: call a function to send the newsletter
                        f -> f.callFn("sendNewsletter", fn(helper::toMap)))
                .build();
    }


}
