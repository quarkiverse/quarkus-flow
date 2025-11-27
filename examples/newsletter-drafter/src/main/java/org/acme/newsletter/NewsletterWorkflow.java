package org.acme.newsletter;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.agent;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.consume;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.emitJson;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.event;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.listen;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.switchWhenOrElse;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.to;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collection;
import org.acme.newsletter.agents.CriticAgent;
import org.acme.newsletter.agents.DrafterAgent;
import org.acme.newsletter.domain.CriticAgentReview;
import org.acme.newsletter.domain.HumanReview;
import org.acme.newsletter.domain.ReviewStatus;
import org.acme.newsletter.services.MailService;

@ApplicationScoped
public class NewsletterWorkflow extends Flow {

    @Inject
    DrafterAgent drafterAgent;

    @Inject
    CriticAgent criticAgent;

    @Inject
    MailService mailService;

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("intelligent-newsletter")
                .tasks(agent("draftAgent", drafterAgent::draft, String.class),
                        agent("criticAgent", criticAgent::critique, String.class),
                        emitJson("draftReady", "org.acme.email.review.required", CriticAgentReview.class),
                        listen("waitHumanReview", to().one(event("org.acme.newsletter.review.done")))
                                .outputAs((Collection<Object> c) -> c.iterator().next()),
                        switchWhenOrElse(h -> ReviewStatus.NEEDS_REVISION.equals(h.status()), "draftAgent", "sendNewsletter", HumanReview.class),
                        consume("sendNewsletter",
                                reviewedDraft -> mailService.send("subscribers@acme.finance.org", "Weekly Newsletter", reviewedDraft.draft()),
                                HumanReview.class))
                .build();
    }

}
