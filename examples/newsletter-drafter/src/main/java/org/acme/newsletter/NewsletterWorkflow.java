package org.acme.newsletter;

import org.acme.newsletter.agents.CriticAgent;
import org.acme.newsletter.agents.DrafterAgent;
import org.acme.newsletter.domain.CriticInput;
import org.acme.newsletter.domain.NewsletterInput;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;


@ApplicationScoped
public class NewsletterWorkflow extends Flow {

    @Inject
    DrafterAgent drafterAgent;

    @Inject
    CriticAgent criticAgent;

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow()
                .tasks(t -> t.callFn(f -> f.function((NewsletterInput input) -> drafterAgent.draft("", input), NewsletterInput.class))
                        /*.outputAs((String) -> {
                            // extract the data
                            // create the new input for the next agent
                            return new CriticInput("", "", "");
                        }))*/
                ).build();
    }
}
