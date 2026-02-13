package io.quarkiverse.flow.it;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import io.serverlessworkflow.impl.WorkflowModel;

@Path("/proposals")
public class ProposalResource {

    @Inject
    SaveProposalWorkflow workflow;

    @POST
    @Produces("text/plain")
    public String proposal() {
        WorkflowModel model = workflow.instance().start().join();
        return "Proposal created with ID: " + model.asNumber().orElseThrow().longValue();
    }
}
