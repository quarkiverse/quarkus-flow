package org.acme.newsletter;

import java.util.Collection;
import java.util.Map;

import org.acme.newsletter.agents.AutoDraftCriticAgent;
import org.acme.newsletter.agents.HumanEditorAgent;
import org.acme.newsletter.domain.HumanReview;
import org.acme.newsletter.domain.NewsletterDraft;
import org.acme.newsletter.domain.NewsletterRequest;
import org.acme.newsletter.services.MailService;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.agent;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.consume;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.emitJson;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.event;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.fn;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.listen;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.switchWhenOrElse;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.to;

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
        return FuncWorkflowBuilder
                .workflow("intelligent-newsletter")
                .tasks(agent("draftAgent", draftAgent::write, NewsletterRequest.class),
                        emitJson("draftReady", "org.acme.email.review.required", NewsletterDraft.class),
                        listen("waitHumanReview", to().one(event("org.acme.newsletter.review.done")))
                                .outputAs((Collection<Object> c) -> c.iterator().next()),
                        switchWhenOrElse(h -> HumanReview.ReviewStatus.NEEDS_REVISION.equals(h.status()), "humanEditorAgent",
                                "sendNewsletter", HumanReview.class)
                                .andThen(a -> a.function("humanEditorAgent", fn(humanEditorAgent::edit, HumanReview.class)
                                        .andThen(f -> f.then("draftReady")))),
                        consume("sendNewsletter", draft -> mailService.send("subscribers@acme.finance.org",
                                draft), NewsletterDraft.class).inputFrom(input -> input.get("draft"), Map.class))
                .build();
    }

}
