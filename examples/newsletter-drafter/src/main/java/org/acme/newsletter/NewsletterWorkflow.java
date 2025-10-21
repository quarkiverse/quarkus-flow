package org.acme.newsletter;

import java.util.Collection;
import java.util.Map;

import org.acme.newsletter.agents.CriticAgent;
import org.acme.newsletter.agents.DrafterAgent;
import org.acme.newsletter.domain.CriticOutput;
import org.acme.newsletter.domain.NewsletterReview;
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
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.listen;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.selectFirst;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.selectFirstStringify;
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
        return FuncWorkflowBuilder.workflow()
                .tasks(agent("draftAgent", drafterAgent::draft, Object.class),
                        agent("criticAgent", criticAgent::critique, Object.class),
                        emitJson("draftReady", "org.acme.email.review.required", CriticOutput.class),
                        listen("waitHumanReview", to().one(event("org.acme.newsletter.review.done")))
                                .outputAs((Collection<Object> c) -> c.iterator().next()),
                        switchWhenOrElse(".status != \"DONE\"", "draftAgent", "sendNewsletter"),
                        consume("sendNewsletter", (Map reviewedDraft) -> {
                            mailService.send("subscribers@acme.finance.org", "Weekly Newsletter", reviewedDraft.get("draft").toString());
                        }, Map.class))
                .build();
    }


}
