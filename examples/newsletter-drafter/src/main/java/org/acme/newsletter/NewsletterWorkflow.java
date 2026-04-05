package org.acme.newsletter;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.agent;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.consume;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.emitJson;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.listen;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.switchWhenOrElse;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.toOne;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
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
        return FuncWorkflowBuilder.workflow("intelligent-newsletter")
                .tasks(agent("draftAgent", draftAgent::write, NewsletterRequest.class),
                        emitJson("draftReady", "org.acme.email.review.required", NewsletterDraft.class),
                        listen("waitHumanReview", toOne("org.acme.newsletter.review.done"))
                                .outputAs((JsonNode node) -> node.isArray() ? node.get(0) : node),
                        switchWhenOrElse(h -> HumanReview.ReviewStatus.NEEDS_REVISION.equals(h.status()),
                                "humanEditorAgent", "sendNewsletter", HumanReview.class),
                        function("humanEditorAgent", humanEditorAgent::edit, HumanReview.class).then("draftReady"),
                        consume("sendNewsletter", draft -> mailService.send("subscribers@acme.finance.org", draft),
                                NewsletterDraft.class).inputFrom(input -> input.get("draft"), Map.class))
                .build();
    }

}
