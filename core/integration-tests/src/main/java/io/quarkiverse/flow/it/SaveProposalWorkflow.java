package io.quarkiverse.flow.it;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Entity;
import jakarta.transaction.Transactional;

import io.quarkiverse.flow.Flow;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class SaveProposalWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("saveProposalWorkflow")
                .tasks(function("doSave", this::save, String.class).outputAs(o -> (Long) o))
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
