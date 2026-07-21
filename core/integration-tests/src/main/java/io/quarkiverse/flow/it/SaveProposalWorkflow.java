package io.quarkiverse.flow.it;

import static io.quarkiverse.flow.dsl.FlowDSL.function;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Entity;
import jakarta.transaction.Transactional;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class SaveProposalWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("saveProposalWorkflow")
                .tasks(function("doSave", this::save, String.class).outputAs((Long o) -> o))
                .build();
    }

    @Transactional
    public Long save(String proposal) {
        Proposal p = new Proposal(proposal);
        p.persist();
        return p.id;
    }

    @Entity
    static class Proposal extends PanacheEntity {

        private String proposal;

        protected Proposal() {
        }

        public Proposal(String proposal) {
            this.proposal = proposal;
        }

        public String getProposal() {
            return proposal;
        }
    }
}
