package io.quarkiverse.flow.persistence.jpa;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

@ApplicationScoped
public class WorkflowInstanceRepository implements PanacheRepositoryBase<WorkflowInstanceEntity, WorkflowInstanceKey> {

}
