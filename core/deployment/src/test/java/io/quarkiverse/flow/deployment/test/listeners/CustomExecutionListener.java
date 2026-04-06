package io.quarkiverse.flow.deployment.test.listeners;

import jakarta.enterprise.context.ApplicationScoped;

import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionCompletableListener;

@ApplicationScoped
public class CustomExecutionListener implements WorkflowExecutionCompletableListener {

}
