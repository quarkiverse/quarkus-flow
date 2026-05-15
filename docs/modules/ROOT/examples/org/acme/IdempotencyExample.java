package org.acme;

import io.serverlessworkflow.impl.WorkflowContextData;

public class IdempotencyExample {

    public void demonstrateInstanceId(WorkflowContextData ctx) {
        // tag::instance-id[]
        // Every workflow instance has a globally unique, stable identifier
        String instanceId = ctx.instanceData().id(); // e.g. "01K9GDCXJVN89V0N4CWVG40R7C"
        // end::instance-id[]
    }
}
