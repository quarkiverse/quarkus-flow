package org.acme.bestpractices;

import jakarta.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.serverlessworkflow.impl.WorkflowError;
import io.serverlessworkflow.impl.WorkflowException;

@ApplicationScoped
public class ApprovalService {

    private static final Logger LOG = LoggerFactory.getLogger(ApprovalService.class.getName());

    public VacationRequest submit(VacationRequest vacationRequest) {
        LOG.info("Submitting approval request");
        return vacationRequest;
    }

    public VacationRequest requireApproval(VacationRequest vacationRequest) {
        LOG.info("Approving request: {}", vacationRequest);
        throw new WorkflowException(WorkflowError.error("APPROVAL_REJECTED", 422).build());
    }

    public Object notifyRejection(VacationRequest vacationRequest) {
        LOG.info("Rejecting request");
        return vacationRequest;
    }
}
