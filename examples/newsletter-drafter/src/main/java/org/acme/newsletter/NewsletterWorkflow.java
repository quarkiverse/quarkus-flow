package org.acme.newsletter;

import static io.quarkiverse.flow.dsl.FlowDSL.agent;
import static io.quarkiverse.flow.dsl.FlowDSL.consume;
import static io.quarkiverse.flow.dsl.FlowDSL.consumed;
import static io.quarkiverse.flow.dsl.FlowDSL.emitJson;
import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowDSL.listen;
import static io.quarkiverse.flow.dsl.FlowDSL.switchWhenOrElse;
import static io.quarkiverse.flow.dsl.FlowDSL.toOne;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import org.acme.newsletter.agents.AutoDraftCriticAgent;
import org.acme.newsletter.agents.HumanEditorAgent;
import org.acme.newsletter.domain.HumanReview;
import org.acme.newsletter.domain.NewsletterDraft;
import org.acme.newsletter.domain.NewsletterRequest;
import org.acme.newsletter.services.MailService;

@ApplicationScoped
public class NewsletterWorkflow extends Flow {

    @Inject
    AutoDraftCriticAgent draftAgent;

    @Inject
    HumanEditorAgent humanEditorAgent;

    @Inject
    MailService mailService;

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("intelligent-newsletter")
                .tasks(agent("draftAgent", draftAgent::write, NewsletterRequest.class),
                        emitJson("draftReady", "org.acme.email.review.required", NewsletterDraft.class),
                        listen("waitHumanReview",
                                toOne(consumed("org.acme.newsletter.review.done").extensionByInstanceId("flowinstanceid"))),
                        switchWhenOrElse(h -> HumanReview.ReviewStatus.NEEDS_REVISION.equals(h.status()),
                                "humanEditorAgent", "sendNewsletter", HumanReview.class),
                        function("humanEditorAgent", humanEditorAgent::edit, HumanReview.class).then("draftReady"),
                        consume("sendNewsletter", draft -> mailService.send("subscribers@acme.finance.org", draft),
                                NewsletterDraft.class).inputFrom(input -> input.get("draft"), Map.class))
                .build();
    }

}
